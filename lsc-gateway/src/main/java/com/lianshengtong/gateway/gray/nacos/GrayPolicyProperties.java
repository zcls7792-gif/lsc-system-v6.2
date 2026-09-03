package com.lianshengtong.gateway.gray.nacos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lianshengtong.gateway.gray.GrayPolicyStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase K：Nacos {@code gray-release.yaml} 的配置模型（通过 {@code @ConfigurationProperties} 绑定，并支持
 * Nacos ConfigService 监听器通过 {@link GrayNacosConfigSync#parseYaml(String)} 热加载）。
 * <p>
 * Nacos 侧约定：
 * <ul>
 *   <li>data-id: {@code gray-release.yaml} （可通过 {@code lsc.gray.nacos.data-id} 覆盖）</li>
 *   <li>group: LSC_GROUP （与 bootstrap 中一致，可覆盖）</li>
 *   <li>refresh: true</li>
 * </ul>
 *
 * <pre>
 * # Nacos gray-release.yaml 示例
 * lsc:
 *   gray:
 *     policies:
 *       - policy-id: order_gray_1001
 *         route-id: order_service_route
 *         baseline-uri: lb://lsc-order-service
 *         canary-uri: lb://lsc-order-service-canary
 *         canary-weight-percent: 10
 *         status: ACTIVE                      # ACTIVE | PAUSED | ROLLED_BACK | GRADUATED
 *         rules:
 *           - type: HEADER
 *             key: X-Gray
 *             operator: EQ
 *             value: canary
 *             extra: force-canary
 *         meta:
 *           created-by: ops@example.com
 *           business: order
 * </pre>
 *
 * <p>
 * 注意：所有字段同时支持 kebab-case（{@code policy-id}）与 camelCase（{@code policyId}），
 * 因为配置类用 {@code @JsonNaming(KebabCaseStrategy)} + {@code @JsonIgnoreProperties(ignoreUnknown=true)} 声明，
 * 可同时覆盖 Nacos YAML 文本、@ConfigurationProperties 绑定与手工 upsert 请求体三种场景。
 */
@Data
@ConfigurationProperties(prefix = "lsc.gray")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
public class GrayPolicyProperties {

    /** （仅用于通过 Nacos Listener 拉取时覆盖 data-id 默认值） */
    private NacosCfg nacos = new NacosCfg();
    /** 读取到的灰度策略列表 */
    private List<PolicyEntry> policies = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    public static class NacosCfg {
        private String dataId = "gray-release.yaml";
        private String group  = "LSC_GROUP";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    public static class PolicyEntry {
        private String policyId;
        private String routeId;
        private String baselineUri;
        private String canaryUri;
        private int canaryWeightPercent;
        private String status = "ACTIVE";
        private List<RuleEntry> rules = new ArrayList<>();
        private Map<String, String> meta = new LinkedHashMap<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    public static class RuleEntry {
        /** HEADER / QUERY / COOKIE / USER_ID_MOD / PATH_PREFIX / JWT_CLAIM */
        private String type;
        private String key;
        /** EQ / NE / IN / CONTAINS / REGEX / MOD / LT / GT / RANGE */
        private String operator;
        private String value;
        /** force-canary / force-baseline / null (默认 force-canary) */
        private String extra;
    }

    // ========== 与 GrayPolicyStore 的双向转换 ==========
    public List<GrayPolicyStore.Policy> toStorePolicies() {
        List<GrayPolicyStore.Policy> out = new ArrayList<>(policies.size());
        for (PolicyEntry e : policies) {
            if (e.getPolicyId() == null || e.getPolicyId().isBlank()) continue;
            List<GrayPolicyStore.Rule> rules = new ArrayList<>(e.getRules().size());
            for (RuleEntry r : e.getRules()) {
                if (r.getType() == null) continue;
                rules.add(new GrayPolicyStore.Rule(
                        r.getType(),
                        defaultStr(r.getKey()),
                        defaultStr(r.getOperator(), "EQ"),
                        defaultStr(r.getValue()),
                        defaultStr(r.getExtra(), "force-canary")));
            }
            GrayPolicyStore.Status st;
            try { st = GrayPolicyStore.Status.valueOf(defaultStr(e.getStatus(), "ACTIVE").toUpperCase()); }
            catch (IllegalArgumentException ex) { st = GrayPolicyStore.Status.ACTIVE; }
            out.add(new GrayPolicyStore.Policy(
                    e.getPolicyId().trim(),
                    defaultStr(e.getRouteId()),
                    defaultStr(e.getBaselineUri()),
                    defaultStr(e.getCanaryUri()),
                    clamp(e.getCanaryWeightPercent(), 0, 100),
                    rules,
                    e.getMeta() == null ? Map.of() : Map.copyOf(e.getMeta()),
                    st,
                    null, null, null     // 时间/操作人留空：GrayPolicyService.createOrUpdate 会在写入 store 时自动填
            ));
        }
        return out;
    }

    private static String defaultStr(String s) { return s == null ? "" : s; }
    private static String defaultStr(String s, String def) { return s == null || s.isBlank() ? def : s; }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
