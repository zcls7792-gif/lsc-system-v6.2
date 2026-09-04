package com.lianshengtong.gateway.gray;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.gateway.gray.spi.GrayPolicyRepository;
import com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository;
import com.lianshengtong.gateway.gray.spi.JdbcGrayPolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase I: GrayPolicyService 行为验证（改写版）。
 * 通过工厂方法直接提供一个预先构造好的 Service（显式关联 Repository 实例），
 * 避免 Service 内部 pickRepository 选择了其他 Bean。
 */
class GrayPolicyServiceTest {

    GrayPolicyStore store;
    InMemoryGrayPolicyRepository repo;
    GrayPolicyService service;
    EmbeddedDatabase jdbcDb;

    @BeforeEach void setUp() {
        store = new GrayPolicyStore();
        repo = new InMemoryGrayPolicyRepository();
        service = buildService(store, repo, null);
    }

    @AfterEach void tearDown() {
        if (jdbcDb != null) try { jdbcDb.shutdown(); } catch (Exception ignored) {}
    }

    /** 工厂：显式指定 repository（避免走 pickRepository 的 Bean 发现机制）。
     * 当 jdbc!=null 时 service 会把它当作"JDBC 存在"的信号，用于 JDBC 优先测试。 */
    static GrayPolicyService buildService(GrayPolicyStore store,
                                          GrayPolicyRepository repo,
                                          JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<GrayPolicyRepository> rp = mock(ObjectProvider.class);
        // 如果 repo 是 JDBC，则只返回 JDBC；否则只返回用户提供的 repo（避免 JDBC 被挑选干扰）
        when(rp.orderedStream()).thenAnswer(inv -> Stream.of(repo));

        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> jp = mock(ObjectProvider.class);
        when(jp.getIfAvailable()).thenReturn(jdbc);

        GrayPolicyService svc = new GrayPolicyService(store, rp, jp, new ObjectMapper()) {
            // 覆盖 pickRepository，直接锁定目标
            @Override
            GrayPolicyRepository pickRepository() {
                return repo;
            }
        };
        svc.init();
        return svc;
    }

    private GrayPolicyStore.Policy sample(String id, int w, GrayPolicyStore.Status s) {
        return GrayPolicyStore.Policy.legacy(id, "r-"+id,
                "lb://svc", "lb://svc-canary", w,
                List.of(), Map.of(), s, Instant.now(), Instant.now(), "ops");
    }

    @Test @DisplayName("createOrUpdate 先内存再持久化；读取两者一致")
    void dualWriteOnCreate() {
        GrayPolicyStore.Policy saved = service.createOrUpdate(sample("p", 5, null), "user");
        assertThat(saved.policyId()).isEqualTo("p");
        assertThat(saved.status()).isEqualTo(GrayPolicyStore.Status.ACTIVE);
        assertThat(saved.canaryWeightPercent()).isEqualTo(5);

        assertThat(store.get("p")).isNotNull();
        assertThat(repo.findById("p"))
                .describedAs("Repository.findById(p) 应该能查到双写的数据")
                .isPresent()
                .get()
                .extracting(GrayPolicyStore.Policy::canaryWeightPercent)
                .isEqualTo(5);

        assertThat(repo.listHistory("p", 10)).extracting(GrayPolicyStore.History::action).containsExactly("CREATE");
        assertThat(service.repositoryImplementation()).contains("InMemory");
        assertThat(service.repositoryAvailable()).isFalse();
    }

    @Test @DisplayName("setWeight / pause / resume / graduate / rollback / delete → 全链路状态正确 + 历史落库")
    void fullLifecycle() {
        service.createOrUpdate(sample("p", 10, null), "ops");

        service.setWeight("p", 50, "ops");
        assertThat(store.get("p").canaryWeightPercent()).isEqualTo(50);
        assertThat(repo.findById("p").get().canaryWeightPercent()).isEqualTo(50);

        service.pause("p", "ops");
        assertThat(store.get("p").status()).isEqualTo(GrayPolicyStore.Status.PAUSED);
        assertThat(repo.findById("p").get().status()).isEqualTo(GrayPolicyStore.Status.PAUSED);

        service.resume("p", "ops");
        assertThat(store.get("p").status()).isEqualTo(GrayPolicyStore.Status.ACTIVE);

        service.rollback("p", "ops", "SLA");
        assertThat(store.get("p").status()).isEqualTo(GrayPolicyStore.Status.ROLLED_BACK);
        assertThat(repo.findById("p").get().status()).isEqualTo(GrayPolicyStore.Status.ROLLED_BACK);

        // 直接删除 ROLLED_BACK → 允许
        boolean deleted = service.delete("p", "ops");
        assertThat(deleted).isTrue();
        assertThat(store.get("p")).isNull();
        assertThat(repo.findById("p")).isEmpty();

        // 历史顺序：DELETE / ROLLBACK / RESUME / PAUSE / WEIGHT_CHANGE / CREATE
        List<String> actions = service.history("p", 20).stream()
                .map(GrayPolicyStore.History::action).toList();
        assertThat(actions).containsExactly("DELETE", "ROLLBACK", "RESUME", "PAUSE", "WEIGHT_CHANGE", "CREATE");
    }

    @Test @DisplayName("ACTIVE 策略 delete 拒绝 → 返回 false 且状态保留；GRADUATE 后允许删除")
    void deleteOnlyGraduatedOrRolledBack() {
        service.createOrUpdate(sample("act", 20, GrayPolicyStore.Status.ACTIVE), "ops");
        boolean removed = service.delete("act", "ops");
        assertThat(removed).isFalse();
        assertThat(store.get("act")).isNotNull();
        assertThat(repo.findById("act"))
                .describedAs("Repository 应该仍有 act 策略（未被删除）")
                .isPresent();

        service.graduate("act", "ops", "released");
        assertThat(store.get("act").status()).isEqualTo(GrayPolicyStore.Status.GRADUATED);
        assertThat(repo.findById("act").get().status()).isEqualTo(GrayPolicyStore.Status.GRADUATED);
        assertThat(service.delete("act", "ops")).isTrue();
        assertThat(store.get("act")).isNull();
    }

    @Test @DisplayName("持久化写入失败 → 内存仍生效，不抛异常")
    void persistFailMemoryOk() {
        GrayPolicyRepository exploding = new InMemoryGrayPolicyRepository() {
            @Override public void save(GrayPolicyStore.Policy policy) { throw new RuntimeException("DB down"); }
            @Override public void appendHistory(GrayPolicyStore.History h) { throw new RuntimeException("DB down"); }
        };
        GrayPolicyStore s2 = new GrayPolicyStore();
        GrayPolicyService sv2 = buildService(s2, exploding, null);

        GrayPolicyStore.Policy saved = sv2.createOrUpdate(sample("x", 15, null), "ops");
        assertThat(saved).isNotNull();
        assertThat(s2.get("x").canaryWeightPercent()).isEqualTo(15);
    }

    @Test @DisplayName("启动加载：Repository 中已存在策略自动注入内存")
    void bootstrapLoadsFromRepo() {
        // 先写 Repository（模拟"上一次运行持久化了策略，重启后读取"）
        GrayPolicyStore.Policy p = sample("booted", 33, GrayPolicyStore.Status.PAUSED);
        repo.save(p);
        repo.appendHistory(new GrayPolicyStore.History(Instant.now(), "booted", "ops", "CREATE", "seed"));

        // 初始化 Service（新内存 store）
        GrayPolicyStore cold = new GrayPolicyStore();
        GrayPolicyService svc = buildService(cold, repo, null);

        assertThat(cold.get("booted"))
                .describedAs("Service init 后，booted 策略应被注入内存")
                .isNotNull();
        assertThat(cold.get("booted").canaryWeightPercent()).isEqualTo(33);
        assertThat(cold.get("booted").status()).isEqualTo(GrayPolicyStore.Status.PAUSED);
    }

    @Test @DisplayName("JDBC 存在时：显式注入 JdbcRepository，启动加载 + save + history 全链路")
    void jdbcRepositoryEndToEnd() {
        jdbcDb = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("svc_jdbc_" + System.nanoTime())
                .build();
        // 初始化 schema
        JdbcTemplate tpl = new JdbcTemplate(jdbcDb);
        GrayPolicyRepositoryContractTest.initH2Schema(jdbcDb);
        JdbcGrayPolicyRepository jdbcRepo = new JdbcGrayPolicyRepository(tpl, new ObjectMapper());

        // 先写入一条策略（模拟 DB 已有）
        jdbcRepo.save(sample("j1", 7, GrayPolicyStore.Status.ACTIVE));

        GrayPolicyStore cold = new GrayPolicyStore();
        GrayPolicyService svc = buildService(cold, jdbcRepo, tpl);
        assertThat(svc.repositoryImplementation()).contains("JdbcGrayPolicyRepository");
        assertThat(svc.repositoryAvailable()).isTrue();

        assertThat(cold.get("j1")).isNotNull();
        assertThat(cold.get("j1").canaryWeightPercent()).isEqualTo(7);

        // 写入 → DB 读取
        svc.createOrUpdate(sample("j2", 30, null), "ops");
        assertThat(jdbcRepo.findById("j2")).isPresent();
        assertThat(jdbcRepo.findById("j2").get().canaryWeightPercent()).isEqualTo(30);
    }
}
