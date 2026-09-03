package com.lianshengtong.gateway.gray;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.gateway.gray.spi.GrayPolicyRepository;
import com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository;
import com.lianshengtong.gateway.gray.spi.JdbcGrayPolicyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 灰度策略 Service 层（对 Controller 与 启动加载 暴露统一入口）。
 * <p>
 * 写入模型：<b>先写 GrayPolicyStore 内存（保证请求热点路径纳秒级读取），再同步写入持久化 Repository</b>。
 * 若持久化写失败，捕获异常并打 WARN，保证已写入内存的策略不会被回滚——避免"DB 抖动导致灰度发布失败"。
 * 后台会输出告警，运维随后可通过手动 save 重放修复。
 *
 * <p>读取模型：
 * <ul>
 *   <li>GrayReleaseGlobalFilter 仍直连 GrayPolicyStore 内存（零序列化开销）。</li>
 *   <li>管理接口详情/列表：GrayPolicyStore 内存读；DB 只作为启动加载 + 审计。</li>
 *   <li>历史：优先读 Repository（若 JDBC 可用则看完整全局历史），否则退化为本地内存 history 环。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrayPolicyService {

    private final GrayPolicyStore store;
    private final ObjectProvider<GrayPolicyRepository> repositoryProvider; // 可能有多个实现或未配置
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;

    private GrayPolicyRepository repository;

    @PostConstruct
    public void init() {
        GrayPolicyRepository selected = pickRepository();
        if (selected.isAvailable()) {
            this.repository = selected;
            bootstrapFromRepository(selected);
        } else {
            this.repository = new InMemoryGrayPolicyRepository();
            log.warn("[gray] No persistent GrayPolicyRepository available (datasource absent?). " +
                    "Using in-memory-only fallback: policies & history will be lost after gateway restart.");
        }
        log.info("[gray] GrayPolicyService initialized using repository={}", repository.getClass().getSimpleName());
    }

    // ========== Repository 选择 ==========
    GrayPolicyRepository pickRepository() {
        // (1) 若用户显式注册了自定义实现（如 Redis/Nacos），按 Bean 直接使用
        List<GrayPolicyRepository> beans = repositoryProvider.orderedStream().toList();
        Optional<GrayPolicyRepository> custom = beans.stream()
                .filter(b -> !(b instanceof JdbcGrayPolicyRepository) && !(b instanceof InMemoryGrayPolicyRepository))
                .findFirst();
        if (custom.isPresent()) return custom.get();

        // (2) JDBC 可用时优先：存在 DataSource → JdbcTemplate 已被 Spring Boot 自动注入
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc != null) {
            JdbcGrayPolicyRepository j = new JdbcGrayPolicyRepository(jdbc, objectMapper);
            if (j.isAvailable()) return j;
        }
        // (3) 已注册的 JDBC Bean（用户手工注册的情况）
        Optional<GrayPolicyRepository> jdbcBean = beans.stream().filter(JdbcGrayPolicyRepository.class::isInstance).findFirst();
        if (jdbcBean.isPresent()) return jdbcBean.get();

        // (4) 兜底：纯内存
        return new InMemoryGrayPolicyRepository();
    }

    private void bootstrapFromRepository(GrayPolicyRepository repo) {
        List<GrayPolicyStore.Policy> loaded;
        try {
            loaded = repo.loadAll();
        } catch (Exception ex) {
            log.error("[gray] Failed to load policies from repository on startup, starting empty: {}", ex.getMessage());
            return;
        }
        // 以 policyId 聚合取最新（防止脏数据多次 upsert 版本冲突），通过 GrayPolicyStore.createOrUpdate 统一写入内存
        Map<String, GrayPolicyStore.Policy> latest = loaded.stream()
                .filter(p -> p != null && p.policyId() != null)
                .collect(Collectors.toMap(GrayPolicyStore.Policy::policyId, p -> p,
                        (a, b) -> a.updatedAt().isAfter(b.updatedAt()) ? a : b));
        for (GrayPolicyStore.Policy p : latest.values()) {
            try {
                // 通过 createOrUpdate 写入内存，但不重复写 history（Repository 已经有审计历史）
                AtomicReference<GrayPolicyStore.Policy> holder = new AtomicReference<>(p);
                // 走反射/直接 put? — 更简单：用 createOrUpdate，但会多写一条 CREATE 历史；
                // 解决：在 memory store 中新增 seed() 方法太打扰，改为：直接写 Repository 即可，
                // 然后再用 store.createOrUpdate 写入内存 + 跳过重复历史：这里复用 store.createOrUpdate 但把 operator 标记为 __BOOTSTRAP__
                // 避免污染审计历史。历史不从内存读，从 Repository 读，所以"多一条内存 CREATE"不会被用户看到。
                store.createOrUpdate(p, "__BOOTSTRAP__");
            } catch (Exception ex) {
                log.error("[gray] Failed to seed policy={} into in-memory store, skip", p.policyId(), ex);
            }
        }
        log.info("[gray] Bootstrapped {} gray policies from repository ({} persisted).", latest.size(), repo.getClass().getSimpleName());
    }

    // ========== 写入 API（内存 → Repository 双写） ==========

    /** 所有写入均先成功更新内存 GrayPolicyStore，再异步/同步地向持久化做 best-effort 落地。
     * 持久化失败不回滚内存状态（保证热路径过滤器零抖动），但会输出 ERROR 日志供监控。
     * 为避免"store 写了但 persist 里的 history 方法又基于 store 查同一条"导致时序/去重复杂，
     * 这里统一调用内部 writeXxx 返回一个 History 对象并立即写入双端。 */

    public GrayPolicyStore.Policy createOrUpdate(GrayPolicyStore.Policy policy, String operator) {
        GrayPolicyStore.Policy saved = store.createOrUpdate(policy, operator);
        persist(rep -> rep.save(saved));
        // history：store 最新写入的该 policyId 第一条动作
        store.history(saved.policyId(), 1).stream().findFirst().ifPresent(h -> persist(rep -> rep.appendHistory(h)));
        return saved;
    }

    public GrayPolicyStore.Policy setWeight(String policyId, int weight, String operator) {
        GrayPolicyStore.Policy next = store.setWeight(policyId, weight, operator);
        if (next != null) appendStoreAndHistory(next, policyId);
        return next;
    }

    public GrayPolicyStore.Policy rollback(String policyId, String operator, String reason) {
        GrayPolicyStore.Policy rolled = store.rollback(policyId, operator, reason);
        if (rolled != null) appendStoreAndHistory(rolled, policyId);
        return rolled;
    }

    public GrayPolicyStore.Policy pause(String policyId, String operator) {
        GrayPolicyStore.Policy next = store.pause(policyId, operator);
        if (next != null) appendStoreAndHistory(next, policyId);
        return next;
    }

    public GrayPolicyStore.Policy resume(String policyId, String operator) {
        GrayPolicyStore.Policy next = store.resume(policyId, operator);
        if (next != null) appendStoreAndHistory(next, policyId);
        return next;
    }

    public GrayPolicyStore.Policy graduate(String policyId, String operator, String reason) {
        GrayPolicyStore.Policy next = store.graduate(policyId, operator, reason);
        if (next != null) appendStoreAndHistory(next, policyId);
        return next;
    }

    public boolean delete(String policyId, String operator) {
        GrayPolicyStore.Policy cur = store.get(policyId);
        if (cur == null) return false;
        boolean removed = store.delete(policyId, operator);
        if (removed) {
            persist(rep -> rep.delete(policyId));
            // 注意：store.delete 自身会追加一条 DELETE 历史（保证内存环审计齐全），
            // 这里直接复用"policyId 最新的那条 DELETE"写入持久化，不再重复构造，避免出现两条 DELETE。
            store.history(null, 50).stream()
                    .filter(h -> "DELETE".equals(h.action()) && policyId.equals(h.policyId()))
                    .findFirst()
                    .ifPresent(h -> persist(rep -> rep.appendHistory(h)));
        }
        return removed;
    }

    private void appendStoreAndHistory(GrayPolicyStore.Policy nextPolicy, String policyId) {
        persist(rep -> rep.save(nextPolicy));
        store.history(policyId, 1).stream().findFirst().ifPresent(h -> persist(rep -> rep.appendHistory(h)));
    }

    // ========== 读取 API ==========
    public List<GrayPolicyStore.History> history(String policyId, int limit) {
        try {
            List<GrayPolicyStore.History> fromRepo = repository.listHistory(policyId, limit);
            // 注意：InMemoryGrayPolicyRepository 在未写入前返回空 List（非 null），这里不把空当作有效结果，
            // 一律合并内存环补全最新记录。策略：取 repository 为主，再加上内存中 repository 未包含的新记录（按 policyId+ts+action+detail 去重）。
            if (fromRepo == null) fromRepo = List.of();
            java.util.Set<String> repoKey = fromRepo.stream().map(this::histKey).collect(java.util.stream.Collectors.toSet());
            List<GrayPolicyStore.History> merged = new java.util.ArrayList<>(fromRepo);
            for (GrayPolicyStore.History h : store.history(policyId, limit * 2)) {
                if (!repoKey.contains(histKey(h))) {
                    merged.add(h);
                    repoKey.add(histKey(h));
                    if (merged.size() >= limit) break;
                }
            }
            // 按 ts 倒序排序
            merged.sort((a, b) -> b.ts().compareTo(a.ts()));
            if (merged.size() > limit) merged = new java.util.ArrayList<>(merged.subList(0, limit));
            if (!merged.isEmpty()) return merged;
        } catch (Exception ex) {
            log.warn("[gray] listHistory from repository failed, fallback to in-memory: {}", ex.getMessage());
        }
        return store.history(policyId, limit);
    }

    private String histKey(GrayPolicyStore.History h) {
        return (h.policyId() == null ? "" : h.policyId()) + "|" + h.ts().toEpochMilli() + "|" + h.action() + "|" + (h.detail() == null ? "" : h.detail());
    }

    /** 对外暴露当前 Repository 类型（用于 Actuator/健康检查）。 */
    public String repositoryImplementation() { return repository.getClass().getSimpleName(); }

    public boolean repositoryAvailable() { return repository.isAvailable() && !(repository instanceof InMemoryGrayPolicyRepository); }

    // ========== internals ==========
    private void persist(java.util.function.Consumer<GrayPolicyRepository> action) {
        try {
            action.accept(repository);
        } catch (Exception ex) {
            // 持久化失败 → 保证内存状态有效；仅记录告警，运维可重放。
            log.error("[gray] Persist to repository failed (in-memory state remains). Error: {}", ex.getMessage());
        }
    }
}
