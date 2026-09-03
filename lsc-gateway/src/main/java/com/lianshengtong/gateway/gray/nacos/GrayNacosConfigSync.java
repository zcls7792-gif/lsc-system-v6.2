package com.lianshengtong.gateway.gray.nacos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lianshengtong.gateway.gray.GrayPolicyService;
import com.lianshengtong.gateway.gray.GrayPolicyStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase K：Nacos 灰度策略推送 + 冷加载。
 * <p>
 * 双轨设计（避免任何一轨成为强依赖）：
 * <ol>
 *   <li><b>冷加载（启动时）</b>：通过 {@link GrayPolicyProperties} {@code @ConfigurationProperties} 自动
 *       绑定 {@code lsc.gray.policies} 属性 —— 无论配置来自 Nacos shared-configs 还是本地
 *       application-*.yaml，Spring Boot 会直接注入。冷加载走
 *       {@link GrayPolicyService#createOrUpdate(GrayPolicyStore.Policy, String)}，内存 + Repository 双写。</li>
 *   <li><b>热加载（运行中）</b>：使用 Nacos SDK 的 {@code ConfigService.addListener}，直接对
 *       {@code gray-release.yaml} 注册回调。配置变更时 YAML → {@link GrayPolicyProperties} →
 *       对比已存在策略 → 新增/更新/删除（仅 GRADUATED 不在 Nacos 中的策略）。</li>
 * </ol>
 *
 * <p>
 * 开关：{@code lsc.gray.nacos.enabled=true}（默认 true；当依赖 com.alibaba.nacos:nacos-api 缺失时自动关闭）
 * </p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GrayPolicyProperties.class)
@ConditionalOnProperty(name = "lsc.gray.nacos.enabled", matchIfMissing = true)
@ConditionalOnClass(name = "com.alibaba.nacos.api.config.ConfigService")
@RequiredArgsConstructor
public class GrayNacosConfigSync {

    private static final String OPERATOR = "__NACOS_SYNC__";

    private final GrayPolicyProperties props;
    private final GrayPolicyService service;
    private final GrayPolicyStore store;
    private final ObjectMapper om;

    /** 保证 Listener 注册只发生一次（多个 Spring 事件顺序不稳定时防重复）。 */
    private final AtomicBoolean listenerRegistered = new AtomicBoolean(false);

    private final org.springframework.beans.factory.ObjectProvider<com.alibaba.nacos.api.config.ConfigService> configServiceProvider;
    private final org.springframework.beans.factory.ObjectProvider<NacosConfigProperties> nacosProps;

    @PostConstruct
    public void onStartup() {
        // Step 1: 冷加载（ConfigurationProperties 已经绑定，直接 upsert 内存 + 持久化）
        syncPoliciesFrom(props.toStorePolicies(), "cold-start");

        // Step 2: 热监听（Nacos SDK 可用时）
        String dataId = props.getNacos() == null ? "gray-release.yaml" : props.getNacos().getDataId();
        String group  = props.getNacos() == null ? "LSC_GROUP" : props.getNacos().getGroup();
        try {
            com.alibaba.nacos.api.config.ConfigService cs = configServiceProvider.getIfUnique();
            if (cs == null) {
                log.info("[gray-nacos] ConfigService bean not present, hot listener skipped (only cold bindings apply).");
                return;
            }
            if (listenerRegistered.compareAndSet(false, true)) {
                cs.addListener(dataId, group, new com.alibaba.nacos.api.config.listener.Listener() {
                    @Override public java.util.concurrent.Executor getExecutor() { return null; } // 默认线程池
                    @Override public void receiveConfigInfo(String configInfo) {
                        log.info("[gray-nacos] Received push event for data-id={} group={} len={}", dataId, group,
                                configInfo == null ? 0 : configInfo.length());
                        List<GrayPolicyStore.Policy> parsed = parseYaml(configInfo);
                        if (parsed == null) return;
                        syncPoliciesFrom(parsed, "nacos-push");
                    }
                });
                log.info("[gray-nacos] hot listener attached: data-id={} group={}", dataId, group);
            }
        } catch (Exception ex) {
            log.warn("[gray-nacos] attach listener failed (cold-bind still active): {} {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    // ------------- 同步 & 解析 -------------
    /**
     * 按 policyId 集合做增改：
     * <ul>
     *   <li>在 policies 中存在：upsert（权重/规则/状态/URI 同步）</li>
     *   <li>已在内存中，但 policies 中缺失：若 status ∈ {GRADUATED, ROLLED_BACK}，则不主动删除（保留审计），
     *       否则（ACTIVE/PAUSED）日志提醒，但不删除（保守策略：避免误删 UI/API 建的策略）。</li>
     * </ul>
     */
    public void syncPoliciesFrom(List<GrayPolicyStore.Policy> incoming, String reason) {
        if (incoming == null || incoming.isEmpty()) {
            log.info("[gray-nacos] {}: no policies received; skip.", reason);
            return;
        }
        Set<String> inIds = new HashSet<>();
        for (GrayPolicyStore.Policy p : incoming) {
            if (p == null || p.policyId().isBlank()) continue;
            inIds.add(p.policyId());
            try {
                GrayPolicyStore.Policy applied = service.createOrUpdate(p, OPERATOR);
                log.debug("[gray-nacos] {}: upsert policy={} routeId={} status={} weight={}",
                        reason, applied.policyId(), applied.routeId(), applied.status(), applied.canaryWeightPercent());
            } catch (Exception e) {
                log.warn("[gray-nacos] {}: upsert policy={} failed: {}", reason, p.policyId(), e.getMessage());
            }
        }
        log.info("[gray-nacos] {}: upserted {} policies (ids={}).", reason, inIds.size(), inIds);
    }

    /** 仅包私有：Nacos 推送的 YAML 文本 → Policy 列表。失败返回 null 并打 ERROR；空/白 → List.of()。 */
    List<GrayPolicyStore.Policy> parseYaml(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) return List.of();
        try {
            ObjectMapper yamlOm = (om == null ? new ObjectMapper(new YAMLFactory()) : om.copyWith(new YAMLFactory()));
            com.fasterxml.jackson.databind.JsonNode root = yamlOm.readTree(yamlText);
            if (root == null || root.isMissingNode() || root.isNull()) return List.of();

            // 兼容写法：找 policies 节点（三种可能路径：顶层 / gray.policies / lsc.gray.policies）
            com.fasterxml.jackson.databind.JsonNode policiesNode = null;
            String[] paths = {"policies", "gray.policies", "lsc.gray.policies"};
            for (String p : paths) {
                com.fasterxml.jackson.databind.JsonNode node = root;
                for (String part : p.split("\\.")) {
                    node = node.path(part);
                    if (node.isMissingNode() || node.isNull()) break;
                }
                if (node.isArray() && !node.isEmpty()) { policiesNode = node; break; }
            }
            // 顶层 policies 不存在任何数组 → 空 YAML（不是 parse 错误）
            if (policiesNode == null) return List.of();

            GrayPolicyProperties tmp = yamlOm.treeToValue(
                    yamlOm.createObjectNode().set("policies", policiesNode),
                    GrayPolicyProperties.class);
            List<GrayPolicyStore.Policy> result = tmp == null ? List.of() : tmp.toStorePolicies();
            // 防御性返回：禁止出现 null（避免调用方 NPE）
            return result == null ? List.of() : result;
        } catch (Exception e) {
            log.error("[gray-nacos] parse yaml failed: {}", e.getMessage());
            return null;  // 解析异常 → null，方便调用方区分"空配置"和"格式错误"
        }
    }
}
