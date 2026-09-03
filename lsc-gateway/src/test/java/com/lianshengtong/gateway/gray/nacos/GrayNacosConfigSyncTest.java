package com.lianshengtong.gateway.gray.nacos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.gateway.gray.GrayPolicyService;
import com.lianshengtong.gateway.gray.GrayPolicyStore;
import com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase K：Nacos 灰度策略推送的纯 Java 单测（不启动 Spring，不依赖 Nacos SDK 实际 ConfigService）。
 * 覆盖：
 *   - @ConfigurationProperties 等价绑定（冷加载路径）
 *   - parseYaml() ：无 lsc 前缀版本 / 有 lsc.gray 前缀版本
 *   - syncPoliciesFrom()：upsert 多条策略，store 内存中可查询
 *   - status 字段大小写 / 空 / 非法值都退化为 ACTIVE
 *   - 空 / null / 异常 YAML → 不抛异常，安全返回空或 null
 */
public class GrayNacosConfigSyncTest {

    private GrayPolicyStore store;
    private GrayNacosConfigSync sync;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new GrayPolicyStore();
        InMemoryGrayPolicyRepository repo = new InMemoryGrayPolicyRepository();
        ObjectProvider<com.lianshengtong.gateway.gray.spi.GrayPolicyRepository> rp = mock(ObjectProvider.class);
        when(rp.orderedStream()).thenAnswer(inv -> Stream.of(repo));
        ObjectProvider<JdbcTemplate> jp = mock(ObjectProvider.class);
        when(jp.getIfAvailable()).thenReturn(null);
        GrayPolicyService service = new GrayPolicyService(store, rp, jp, new ObjectMapper());
        service.init();

        GrayPolicyProperties props = new GrayPolicyProperties();
        ObjectProvider<com.alibaba.nacos.api.config.ConfigService> noprovider = mock(ObjectProvider.class);
        when(noprovider.getIfUnique()).thenReturn(null);
        ObjectProvider<com.alibaba.cloud.nacos.NacosConfigProperties> nacosProps = mock(ObjectProvider.class);
        when(nacosProps.getIfUnique()).thenReturn(null);
        sync = new GrayNacosConfigSync(props, service, store, new ObjectMapper(), noprovider, nacosProps);
    }

    @Test
    void propertiesColdBinding_updatesStore() {
        GrayPolicyProperties props = new GrayPolicyProperties();
        GrayPolicyProperties.PolicyEntry e = new GrayPolicyProperties.PolicyEntry();
        e.setPolicyId("cold-p1");
        e.setRouteId("order");
        e.setBaselineUri("lb://a");
        e.setCanaryUri("lb://a-canary");
        e.setCanaryWeightPercent(15);
        GrayPolicyProperties.RuleEntry r = new GrayPolicyProperties.RuleEntry();
        r.setType("HEADER"); r.setKey("X-Env"); r.setOperator("EQ"); r.setValue("canary");
        e.setRules(List.of(r));
        e.setMeta(Map.of("biz", "order"));
        props.setPolicies(List.of(e));

        sync.syncPoliciesFrom(props.toStorePolicies(), "cold-start");

        GrayPolicyStore.Policy got = store.get("cold-p1");
        assertNotNull(got);
        assertEquals("order", got.routeId());
        assertEquals(15, got.canaryWeightPercent());
        assertEquals(GrayPolicyStore.Status.ACTIVE, got.status());
        assertEquals(1, got.rules().size());
        assertEquals("HEADER", got.rules().get(0).type());
        assertEquals("order", got.meta().get("biz"));
    }

    @Test
    void parseYaml_prefixedLscGray() {
        String yaml =
                "lsc:\n" +
                "  gray:\n" +
                "    policies:\n" +
                "      - policy-id: yaml-p1\n" +
                "        route-id: r1\n" +
                "        baseline-uri: lb://a\n" +
                "        canary-uri: lb://a-c\n" +
                "        canary-weight-percent: 20\n" +
                "        status: PAUSED\n";
        List<GrayPolicyStore.Policy> list = sync.parseYaml(yaml);
        System.out.println("DEBUG lsc-gray list=" + list);
        assertNotNull(list, "list was null; parseYaml returned null (invalid yaml or treeToValue failed)");
        assertEquals(1, list.size());
        assertEquals("yaml-p1", list.get(0).policyId());
        assertEquals(20, list.get(0).canaryWeightPercent());
        assertEquals(GrayPolicyStore.Status.PAUSED, list.get(0).status());
    }

    @Test
    void parseYaml_topLevelGray() {
        String yaml =
                "gray:\n" +
                "  policies:\n" +
                "    - policy-id: yaml-p2\n" +
                "      route-id: r2\n" +
                "      baseline-uri: lb://b\n" +
                "      canary-uri: lb://b-c\n" +
                "      canary-weight-percent: 101\n" +   // 越界值 → clamp 到 100
                "      status: INVALID_BAD\n"; // 非法 status → ACTIVE
        List<GrayPolicyStore.Policy> list = sync.parseYaml(yaml);
        System.out.println("DEBUG top-level-gray list=" + list);
        assertNotNull(list, "list should not be null for syntactically valid yaml");
        assertEquals(1, list.size());
        assertEquals("yaml-p2", list.get(0).policyId());
        assertEquals(100, list.get(0).canaryWeightPercent());
        assertEquals(GrayPolicyStore.Status.ACTIVE, list.get(0).status());
    }

    @Test
    void parseYaml_nullOrEmpty_returnsEmpty() {
        assertEquals(List.of(), sync.parseYaml(null));
        assertEquals(List.of(), sync.parseYaml(""));
        assertEquals(List.of(), sync.parseYaml("   \n"));
    }

    @Test
    void parseYaml_invalidYaml_returnsNullNoThrow() {
        // SnakeYAML 明确禁止 TAB 缩进 → 抛 MarkedYAMLException；不应向上传播
        assertDoesNotThrow(() -> sync.parseYaml("\t\t:\n:a: :b"));
        assertNull(sync.parseYaml("\t\t:\n:a: :b"), "syntax error yaml must return null to signal parse failure");
    }

    @Test
    void syncPoliciesFrom_updatesExistingPolicy() {
        // 第一步：创建一条
        GrayPolicyStore.Policy v1 = buildPolicy("upd-p1", "rA", 30, GrayPolicyStore.Status.ACTIVE);
        sync.syncPoliciesFrom(List.of(v1), "step1");
        assertEquals(30, store.get("upd-p1").canaryWeightPercent());
        // 第二步：改权重 + 切 PAUSED
        GrayPolicyStore.Policy v2 = buildPolicy("upd-p1", "rA", 0, GrayPolicyStore.Status.PAUSED);
        sync.syncPoliciesFrom(List.of(v2), "nacos-push");
        GrayPolicyStore.Policy got = store.get("upd-p1");
        assertEquals(0, got.canaryWeightPercent());
        assertEquals(GrayPolicyStore.Status.PAUSED, got.status());
        // 操作历史：应该至少有 CREATE + UPDATE 两次
        List<GrayPolicyStore.History> h = store.history("upd-p1", 10);
        assertTrue(h.stream().anyMatch(x -> "CREATE".equals(x.action())));
        assertTrue(h.stream().anyMatch(x -> "UPDATE".equals(x.action())));
    }

    @Test
    void syncPoliciesFrom_emptyList_noChange() {
        GrayPolicyStore.Policy v1 = buildPolicy("p-empty", "rX", 10, GrayPolicyStore.Status.ACTIVE);
        sync.syncPoliciesFrom(List.of(v1), "step1");
        assertNotNull(store.get("p-empty"));
        sync.syncPoliciesFrom(List.of(), "empty-push");
        assertNotNull(store.get("p-empty")); // 不空删除
    }

    private static GrayPolicyStore.Policy buildPolicy(String pid, String routeId, int weight, GrayPolicyStore.Status s) {
        return new GrayPolicyStore.Policy(pid, routeId, "lb://base", "lb://canary", weight, List.of(), Map.of(), s,
                Instant.now(), Instant.now(), "test");
    }
}
