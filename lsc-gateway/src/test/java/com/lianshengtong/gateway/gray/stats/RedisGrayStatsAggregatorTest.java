package com.lianshengtong.gateway.gray.stats;

import com.lianshengtong.gateway.gray.GrayPolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase K：Redis GrayStatsAggregator 单元测试（mock ReactiveStringRedisTemplate，避免起 EmbeddedRedis）。
 * <p>
 * 同时验证 LocalOnly 兜底以及 /stats /summary 调用链视图。
 */
public class RedisGrayStatsAggregatorTest {

    private GrayPolicyStore store;

    @BeforeEach
    void setUp() {
        store = new GrayPolicyStore();
        GrayPolicyStore.Policy p = GrayPolicyStore.Policy.legacy(
                "p1", "r1", "lb://baseline", "lb://canary", 10,
                List.of(), Map.of(), GrayPolicyStore.Status.ACTIVE,
                Instant.now(), Instant.now(), "test");
        store.createOrUpdate(p, "test");
        // 先写一些本地数据，便于验证 Redis 值 + 本地值加总
        GrayPolicyStore.Stats s = store.statsFor("p1");
        s.canaryHits.set(20);
        s.baselineHits.set(80);
        s.ruleForceCanary.set(3);
        s.ruleForceBaseline.set(2);
    }

    @SuppressWarnings("unchecked")
    private ReactiveStringRedisTemplate mockRedisForRead() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> valOps = mock(ReactiveValueOperations.class);
        ReactiveSetOperations<String, String> setOps = mock(ReactiveSetOperations.class);
        when(redis.opsForValue()).thenReturn(valOps);
        when(redis.opsForSet()).thenReturn(setOps);
        return redis;
    }

    @SuppressWarnings("unchecked")
    private ReactiveStringRedisTemplate stubReadHits(
            ReactiveStringRedisTemplate redis,
            String baselineHits, String canaryHits, String rfc, String rfb, String startTs, long liveNodesSize) {
        ReactiveValueOperations<String, String> valOps = redis.opsForValue();
        ReactiveSetOperations<String, String> setOps = redis.opsForSet();
        when(valOps.multiGet(any(List.class))).thenReturn(Mono.just(List.of(baselineHits, canaryHits, rfc, rfb, startTs)));
        when(setOps.size(anyString())).thenReturn(Mono.just(liveNodesSize));
        return redis;
    }

    @Test
    void aggregated_returnsLocalPlusClusterSum() {
        ReactiveStringRedisTemplate redis = mockRedisForRead();
        stubReadHits(redis, "120", "80", "5", "1", "1700000000", 3L);
        RedisGrayStatsAggregator agg = new RedisGrayStatsAggregator(redis, store, "pfx");
        GrayStatsAggregator.AggregatedStats a = agg.aggregated("p1");
        // cluster=120/80 加 local=80/20
        assertEquals(120 + 80, a.baselineHits());
        assertEquals(80 + 20, a.canaryHits());
        assertEquals(5 + 3, a.ruleForceCanary());
        assertEquals(1 + 2, a.ruleForceBaseline());
        assertEquals(1700000000L, a.startEpochSec());
        assertEquals(3, a.liveNodes());
        assertTrue(a.clusterAvailable());
    }

    @Test
    void aggregated_fallBackToLocalOnRedisError() {
        ReactiveStringRedisTemplate redis = mockRedisForRead();
        ReactiveValueOperations<String, String> valOps = redis.opsForValue();
        when(valOps.multiGet(any(List.class))).thenReturn(Mono.error(new RuntimeException("Redis broken")));
        RedisGrayStatsAggregator agg = new RedisGrayStatsAggregator(redis, store, "pfx");
        GrayStatsAggregator.AggregatedStats a = agg.aggregated("p1");
        assertEquals(80, a.baselineHits());
        assertEquals(20, a.canaryHits());
        assertFalse(a.clusterAvailable());
    }

    @Test
    void record_callsReactiveOpsFireAndForget() {
        ReactiveStringRedisTemplate redis = mockRedisForRead();
        ReactiveValueOperations<String, String> valOps = redis.opsForValue();
        ReactiveSetOperations<String, String> setOps = redis.opsForSet();
        // record() 内部链式：incr × (1 hit + 0~1 ruleForce) + setIfAbsent(start_ts)
        //   + set.expire(start_ts) + sadd(live_nodes) + redis.expire(live_nodes) + set(memberKey)
        //   + redis.expire(any member key)
        when(valOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(valOps.setIfAbsent(anyString(), anyString())).thenReturn(Mono.just(true));
        when(valOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOps.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOps.size(anyString())).thenReturn(Mono.just(2L));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        RedisGrayStatsAggregator agg = new RedisGrayStatsAggregator(redis, store, "pfx");
        assertDoesNotThrow(() -> agg.record("p1", GrayStatsAggregator.Version.CANARY, GrayStatsAggregator.RuleForce.FORCE_CANARY));
        // 给 fire-and-forget subscribe 一些时间执行
        try { Thread.sleep(80L); } catch (InterruptedException ignored) { }
        verify(valOps, atLeastOnce()).increment(anyString());
    }

    @Test
    void localOnlyAggregator_returnsLocalOnly() {
        LocalOnlyGrayStatsAggregator agg = new LocalOnlyGrayStatsAggregator(store);
        var a = agg.aggregated("p1");
        assertEquals(80, a.baselineHits());
        assertEquals(20, a.canaryHits());
        assertFalse(a.clusterAvailable());
        assertEquals(1, a.liveNodes());

        Map<String, GrayStatsAggregator.AggregatedStats> all = agg.aggregatedAll();
        assertEquals(1, all.size());
        assertTrue(all.containsKey("p1"));
    }
}
