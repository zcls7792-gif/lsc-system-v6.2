package com.lianshengtong.gateway.gray.stats;

import com.lianshengtong.gateway.gray.GrayPolicyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reactive Redis 实现（基于 Spring WebFlux 的 {@link ReactiveStringRedisTemplate}，Gateway 栈友好）。
 * <p>
 * 写入：热路径 filter 调用 record() → 本地同步写入 GrayPolicyStore.Stats（由调用方完成，实现仅负责 Redis 端聚合计数）
 *        同时向 Redis 发一个非阻塞的 reactive INCR / SADD 请求（订阅式 fire-and-forget，不影响 Flux/ Mono 主链路）。
 * 读取：/stats & /summary 在 Controller 层通过 block(Duration) 聚合，典型 1~2ms。
 * <p>
 * Redis 不可用（ReactiveStringRedisTemplate bean 缺失）时回退到 LocalOnlyGrayStatsAggregator。
 */
@Slf4j
public class RedisGrayStatsAggregator implements GrayStatsAggregator {

    private static final Duration NODE_TTL = Duration.ofMinutes(30);
    private static final Duration START_TS_TTL = Duration.ofDays(30);

    private final ReactiveStringRedisTemplate redis;
    private final GrayPolicyStore store;
    private final String prefix;
    private final String nodeId;

    public RedisGrayStatsAggregator(ReactiveStringRedisTemplate redis,
                                   GrayPolicyStore store,
                                   String prefix) {
        this.redis = redis;
        this.store = store;
        this.prefix = prefix == null || prefix.isBlank() ? "lsc:gray:stats" : prefix;
        this.nodeId = resolveNodeId();
    }

    @Override
    public void record(String policyId, Version version, RuleForce ruleForce) {
        try {
            String baseHitKey = null;
            if (version == Version.BASELINE) baseHitKey = key(policyId, "baseline_hits");
            else if (version == Version.CANARY) baseHitKey = key(policyId, "canary_hits");
            final String finalHitKey = baseHitKey;

            Mono<Void> op = Mono.empty();

            if (finalHitKey != null) {
                op = op.then(redis.opsForValue().increment(finalHitKey).then());
            }
            if (ruleForce == RuleForce.FORCE_CANARY) {
                op = op.then(redis.opsForValue().increment(key(policyId, "rule_force_canary")).then());
            } else if (ruleForce == RuleForce.FORCE_BASELINE) {
                op = op.then(redis.opsForValue().increment(key(policyId, "rule_force_baseline")).then());
            }
            // 写入 start_ts（SET_IF_ABSENT → SETNX 语义，保留第一次写入时间），TTL 30 天
            op = op.then(redis.opsForValue()
                    .setIfAbsent(key(policyId, "start_ts"), String.valueOf(System.currentTimeMillis() / 1000L))
                    .flatMap(ok -> Boolean.TRUE.equals(ok)
                            ? redis.expire(key(policyId, "start_ts"), START_TS_TTL)
                            : Mono.just(false))
                    .then());
            // 活跃节点集合：当前节点 SADD + 单独给该节点键 EXPIRE（30 分钟）
            String memberKey = key(policyId, "live_nodes:" + nodeId);
            op = op.then(redis.opsForSet().add(key(policyId, "live_nodes"), nodeId)
                    .flatMap(n -> redis.expire(key(policyId, "live_nodes"), NODE_TTL))
                    .flatMap(n -> redis.opsForValue().set(memberKey, "1", NODE_TTL))
                    .then());

            // fire-and-forget：订阅即可
            op.onErrorResume(e -> {
                log.warn("[gray-stats] Redis record failed for policy={}: {}", policyId, e.getMessage());
                return Mono.empty();
            }).subscribe();
        } catch (Exception e) {
            log.warn("[gray-stats] record() unexpected error for policy={}: {}", policyId, e.getMessage());
        }
    }

    @Override
    public AggregatedStats aggregated(String policyId) {
        try {
            return redis.opsForValue()
                    .multiGet(List.of(
                            key(policyId, "baseline_hits"),
                            key(policyId, "canary_hits"),
                            key(policyId, "rule_force_canary"),
                            key(policyId, "rule_force_baseline"),
                            key(policyId, "start_ts")
                    )).zipWith(redis.opsForSet().size(key(policyId, "live_nodes")).defaultIfEmpty(0L))
                    .map(tuple -> {
                        List<String> vals = tuple.getT1() == null ? List.of() : tuple.getT1();
                        long base = toLong(at(vals, 0));
                        long can = toLong(at(vals, 1));
                        long rfc = toLong(at(vals, 2));
                        long rfb = toLong(at(vals, 3));
                        long startSec = toLong(at(vals, 4));
                        int liveNodes = tuple.getT2().intValue();
                        // 将本地值加到 cluster 值上（防止 Redis 抖动时数据被"抹零"）
                        GrayPolicyStore.Stats s = store.statsFor(policyId);
                        return new AggregatedStats(
                                base + s.baselineHits.get(),
                                can + s.canaryHits.get(),
                                rfc + s.ruleForceCanary.get(),
                                rfb + s.ruleForceBaseline.get(),
                                startSec == 0 ? s.startTimeMs.get() / 1000L : startSec,
                                Math.max(liveNodes, 1),
                                true);
                    })
                    .onErrorReturn(localOnly(policyId))
                    .block(Duration.ofMillis(1500));
        } catch (Exception e) {
            log.warn("[gray-stats] aggregated({}) fall back to local: {}", policyId, e.getMessage());
            return localOnly(policyId);
        }
    }

    @Override
    public Map<String, AggregatedStats> aggregatedAll() {
        List<String> policyIds = store.list().stream()
                .map(GrayPolicyStore.Policy::policyId).distinct().toList();
        if (policyIds.isEmpty()) return Map.of();
        try {
            // 用 pipeline：把所有 policy 的 GET/SCARD 打包
            Flux<Map.Entry<String, AggregatedStats>> flux = Flux.fromIterable(policyIds)
                    .flatMap(pid -> Mono.fromCallable(() -> Map.entry(pid, aggregated(pid))));
            Map<String, AggregatedStats> m = new LinkedHashMap<>();
            flux.toStream().forEach(e -> m.put(e.getKey(), e.getValue()));
            return m;
        } catch (Exception e) {
            log.warn("[gray-stats] aggregatedAll fall back to local-only: {}", e.getMessage());
            return policyIds.stream().collect(Collectors.toMap(Function.identity(), this::localOnly));
        }
    }

    // -------- internals --------
    private AggregatedStats localOnly(String policyId) {
        GrayPolicyStore.Stats s = store.statsFor(policyId);
        return new AggregatedStats(
                s.baselineHits.get(),
                s.canaryHits.get(),
                s.ruleForceCanary.get(),
                s.ruleForceBaseline.get(),
                s.startTimeMs.get() / 1000L,
                1,
                false
        );
    }

    private static <T> T at(List<T> list, int idx) {
        return (list == null || idx >= list.size()) ? null : list.get(idx);
    }

    private static long toLong(Object s) {
        if (s == null) return 0L;
        try { return Long.parseLong(String.valueOf(s)); }
        catch (NumberFormatException ex) { return 0L; }
    }

    private String key(String policyId, String field) {
        return prefix + ":policy:" + policyId + ":" + field;
    }

    private static String resolveNodeId() {
        String host;
        try { host = InetAddress.getLocalHost().getHostAddress(); }
        catch (UnknownHostException ex) { host = "127.0.0.1"; }
        String port = System.getProperty("server.port", System.getenv().getOrDefault("SERVER_PORT", "8000"));
        return host + ":" + port + ":" + Long.toHexString(System.nanoTime() & 0xFFFFFL);
    }
}
