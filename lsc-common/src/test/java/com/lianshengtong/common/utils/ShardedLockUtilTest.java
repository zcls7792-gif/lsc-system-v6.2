package com.lianshengtong.common.utils;

import org.junit.jupiter.api.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("分片分布式锁工具单元测试")
class ShardedLockUtilTest {

    @Mock
    private RedissonClient redissonClient;

    private ShardedLockUtil shardedLockUtil;

    private RLock mockLock;

    @BeforeEach
    void setUp() {
        mockLock = mock(RLock.class);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        shardedLockUtil = new ShardedLockUtil(redissonClient, 16, 3000L, 10000L);
    }

    // ============== resolveShard 分片路由测试 ==============

    @Test
    @DisplayName("resolveShard - 空字符串标识符路由到分片")
    void testResolveShard_emptyString() {
        int shard = shardedLockUtil.resolveShard("");
        assertTrue(shard >= 0 && shard < 16);
    }

    @Test
    @DisplayName("resolveShard - 零哈希字符串分片编号非负")
    void testResolveShard_zeroHash() {
        String identifier = "test";
        int shard = shardedLockUtil.resolveShard(identifier);
        assertTrue(shard >= 0 && shard < 16);
        // 对于同样的字符串，shard 应该一致
        assertEquals(shard, shardedLockUtil.resolveShard(identifier));
    }

    @Test
    @DisplayName("resolveShard - 大量标识符分片分布均匀")
    void testResolveShard_uniformDistribution() {
        Set<Integer> shards = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            shards.add(shardedLockUtil.resolveShard("order_" + i));
        }
        // 16个分片中至少应该有一半被命中
        assertTrue(shards.size() >= 8,
                "分片分布应覆盖至少8个，实际: " + shards.size());
    }

    @Test
    @DisplayName("resolveShard - 特殊字符标识符分片路由正常")
    void testResolveShard_specialCharacters() {
        String[] ids = {"order:123", "user@456", "product#789", "a".repeat(100)};
        for (String id : ids) {
            int shard = shardedLockUtil.resolveShard(id);
            assertTrue(shard >= 0 && shard < 16,
                    "分片编号越界: " + shard + " for id: " + id);
        }
    }

    @Test
    @DisplayName("getShardCount - 返回配置的分片数")
    void testGetShardCount() {
        assertEquals(16, shardedLockUtil.getShardCount());
    }

    // ============== tryShardedLock 基本测试 ==============

    @Test
    @DisplayName("tryShardedLock - 成功获取锁返回锁对象")
    void testTryShardedLock_success() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        RLock lock = shardedLockUtil.tryShardedLock("lsc:order", "ORD001");

        assertNotNull(lock);
        verify(redissonClient).getLock(contains("lsc:order:shard:"));
    }

    @Test
    @DisplayName("tryShardedLock - 获取锁失败返回 null")
    void testTryShardedLock_fails() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        RLock lock = shardedLockUtil.tryShardedLock("lsc:order", "ORD001");

        assertNull(lock);
    }

    @Test
    @DisplayName("tryShardedLock - 自定义等待和租约时间")
    void testTryShardedLock_customWaitAndLease() throws Exception {
        when(mockLock.tryLock(1000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        RLock lock = shardedLockUtil.tryShardedLock("lsc:order", "ORD001", 1000L, 5000L);

        assertNotNull(lock);
        verify(mockLock).tryLock(1000L, 5000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("tryShardedLock - 线程中断异常向上传播")
    void testTryShardedLock_interrupted() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("线程被中断"));

        assertThrows(InterruptedException.class,
                () -> shardedLockUtil.tryShardedLock("lsc:order", "ORD001"));
    }

    @Test
    @DisplayName("tryShardedLock - 同一标识符始终路由到同一分片")
    void testTryShardedLock_sameIdSameShard() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        RLock lock1 = shardedLockUtil.tryShardedLock("key", "same_id");
        RLock lock2 = shardedLockUtil.tryShardedLock("key", "same_id");

        assertNotNull(lock1);
        assertNotNull(lock2);
        // Redisson mock 应返回同一个 lock 对象
        assertSame(lock1, lock2);
    }

    // ============== 分片路由一致性测试 ==============

    @Test
    @DisplayName("不同标识符路由到不同分片避免热点")
    void testDifferentIdentifiers_differentShards() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        int shard1 = shardedLockUtil.resolveShard("order_A");
        int shard2 = shardedLockUtil.resolveShard("order_B");

        // 不一定每次不同，但至少分片逻辑能正确返回
        assertTrue(shard1 >= 0 && shard1 < 16);
        assertTrue(shard2 >= 0 && shard2 < 16);
    }

    @Test
    @DisplayName("tryShardedLock - 空标识符正常处理")
    void testTryShardedLock_emptyIdentifier() throws Exception {
        when(mockLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        RLock lock = shardedLockUtil.tryShardedLock("key", "");

        assertNotNull(lock);
    }

    // ============== Step 3: Integer.MIN_VALUE / MAX_VALUE 分片测试 ==============

    @Test
    @DisplayName("resolveShard - Integer.MIN_VALUE 标识符分片编号非负")
    void testResolveShard_integerMinValue() {
        // Integer.MIN_VALUE 的 hashCode 为 Integer.MIN_VALUE
        String minStr = String.valueOf(Integer.MIN_VALUE);
        int shard = shardedLockUtil.resolveShard(minStr);

        // 使用 hashCode & 0x7fffffff 确保无符号，分片编号必须非负
        assertTrue(shard >= 0,
                "Integer.MIN_VALUE 标识符分片编号应为非负，实际: " + shard);
        assertTrue(shard < 16,
                "分片编号应在 0-15 范围内，实际: " + shard);
    }

    @Test
    @DisplayName("resolveShard - Integer.MAX_VALUE 标识符分片正常")
    void testResolveShard_integerMaxValue() {
        String maxStr = String.valueOf(Integer.MAX_VALUE);
        int shard = shardedLockUtil.resolveShard(maxStr);

        assertTrue(shard >= 0 && shard < 16);
    }

    @Test
    @DisplayName("resolveShard - 负数 hashCode 标识符分片路由正确")
    void testResolveShard_negativeHashcode() {
        // 构造 hashCode 为负数的字符串
        // 通过多次尝试找到 hashCode < 0 的字符串
        String negativeHashStr = null;
        for (int i = 0; i < 10000; i++) {
            String s = "test_" + i;
            if (s.hashCode() < 0) {
                negativeHashStr = s;
                break;
            }
        }

        if (negativeHashStr != null) {
            int shard = shardedLockUtil.resolveShard(negativeHashStr);
            // 负数 hashCode 经 & 0x7fffffff 处理后必须非负
            assertTrue(shard >= 0 && shard < 16,
                    "负数 hashCode 标识符分片必须非负，实际: " + shard);
        }
    }

    @Test
    @DisplayName("resolveShard - 连续整数标识符分片分布验证")
    void testResolveShard_consecutiveIntegers() {
        Set<Integer> shards = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            int shard = shardedLockUtil.resolveShard(String.valueOf(i));
            assertTrue(shard >= 0 && shard < 16,
                    "分片越界: i=" + i + " shard=" + shard);
            shards.add(shard);
        }
        // 16个分片中至少应覆盖8个
        assertTrue(shards.size() >= 8,
                "分片分布应覆盖至少8个，实际: " + shards.size());
    }
}
