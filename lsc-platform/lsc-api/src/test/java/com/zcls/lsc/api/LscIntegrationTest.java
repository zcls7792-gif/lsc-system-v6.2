package com.zcls.lsc.api;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.order.OrderService;
import com.zcls.lsc.risk.rule.RiskInterceptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.lifecycle.Startables;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSC 全链路集成测试 — Testcontainers (MySQL 8 + Redis 7)。
 *
 * 验证场景：
 *   1. 应用上下文正常启动（MySQL/Redis 已就绪）
 *   2. V1-V9 迁移脚本自动执行（Flyway via Spring Boot datasource init）
 *   3. 风控规则引擎：B 端大额下单命中阻断
 *   4. 风控规则引擎：大额退款命中阻断
 *   5. 风控规则引擎：权益发放（ALERT 类）不阻断
 *
 * 运行前提：本地有 Docker。
 * 运行方式：mvn -pl lsc-api test -Dtest=LscIntegrationTest
 */
@Testcontainers
@SpringBootTest
class LscIntegrationTest {

    private static final Network NET = Network.newNetwork();

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withNetwork(NET)
            .withNetworkAliases("mysql")
            .withDatabaseName("lsc")
            .withUsername("lsc")
            .withPassword("lsc")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--skip-character-set-client-handshake");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withNetwork(NET)
            .withNetworkAliases("redis")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        // 并行启动两个容器（比串行启动快很多）
        Startables.deepStart(Stream.of(MYSQL, REDIS)).join();

        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&allowPublicKeyRetrieval=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        r.add("spring.data.redis.host", () -> "localhost");
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        // Redisson 走 spring data redis 配置
        r.add("spring.redis.host", () -> "localhost");
        r.add("spring.redis.port", () -> REDIS.getMappedPort(6379).toString());

        // 关闭 Flyway（迁移由 schema.sql 或本测试手动驱动）
        r.add("spring.flyway.enabled", () -> "false");
        // 启用 Spring Boot 的 schema 自动初始化（执行 V1-V9）
        r.add("spring.sql.init.mode", () -> "always");
        r.add("spring.sql.init.schema-locations", () -> schemaLocations());
    }

    private static String schemaLocations() {
        List<String> files = List.of(
                "V1__lsc_core", "V2__lsc_order", "V3__lsc_release", "V4__lsc_coupon",
                "V5__lsc_master", "V6__quote", "V7__lsc_scheduler",
                "V8__lsc_rule_engine", "V9__lsc_rule_seed"
        );
        return files.stream()
                .map(f -> "classpath:db/migration/" + f + ".sql")
                .collect(Collectors.joining(","));
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private RiskInterceptionService riskInterception;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        // 1) Spring 上下文正常启动（验证 MySQL + Redis 连通性 + 所有 Bean 装配）
        assertNotNull(orderService);
        assertNotNull(riskInterception);
    }

    @Test
    void ruleSeedDataLoaded() throws SQLException {
        // 2) 验证 V9 预置规则确实加载
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            // 仅断言数量与关键规则存在
            var rs = s.executeQuery("SELECT COUNT(*) FROM risk_rule WHERE status='ACTIVE'");
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 5, "预置规则应 >= 5 条");
        }
    }

    @Test
    void bSuperLargeOrderIsBlockedByRiskRule() {
        // 3) B 端用户下单 60,000 元（= 6,000,000 分）
        //    命中 R2 ORDER_B_SUPER_LARGE（REVIEW，阻断）
        //    注：本测试不真实创建 quote，只验证拦截器被调用并抛 BENEFIT_BLOCKED
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> riskInterception.checkOrderCreation(9001L, 6_000_000L, "B"));
        // 至少有一条 REVIEW 规则被命中
        assertEquals(ErrorCode.BENEFIT_BLOCKED, ex.errorCode());
    }

    @Test
    void largeRefundIsBlockedByRiskRule() {
        // 4) 退款 5,000 元（= 500,000 分）
        //    命中 R3 REFUND_LARGE_AMOUNT（REVIEW，阻断）
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> riskInterception.checkRefund(9001L, 2001L, 500_000L, "QTY"));
        assertEquals(ErrorCode.BENEFIT_BLOCKED, ex.errorCode());
    }

    @Test
    void alertRuleDoesNotBlockGrant() {
        // 5) 权益发放场景命中 ALERT（不阻断）
        //    R5 GRANT_LOG 是 ALERT，triggered=false
        assertDoesNotThrow(() -> riskInterception.checkGrant(9001L, 2001L));
    }

    @Test
    void smallOrderPassesAllRules() {
        // 6) C 端小额下单：100 元 = 10,000 分
        //    不命中任何阈值（< 1,000,000 分），不阻断
        assertDoesNotThrow(() -> riskInterception.checkOrderCreation(9001L, 10_000L, "C"));
    }
}
