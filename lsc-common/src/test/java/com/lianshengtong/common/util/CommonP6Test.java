package com.lianshengtong.common.util;

import com.lianshengtong.common.lock.DistributedLock;
import com.lianshengtong.common.security.LogSanitizer;
import com.lianshengtong.common.sharding.ShardingRouter;
import com.lianshengtong.common.utils.AesEncryptUtil;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LSC Common P6 最终低覆盖率类单元测试")
@ExtendWith(MockitoExtension.class)
class CommonP6Test {

    // ==================== AesEncryptUtil 测试 ====================

    @Test
    @DisplayName("AesEncryptUtil: encrypt 超长字符串加解密正确")
    void aesEncryptUtil_encryptDecrypt_veryLongString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("敏感数据");
        }
        String plain = sb.toString();
        String encrypted = AesEncryptUtil.encrypt(plain);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("AesEncryptUtil: decrypt 无效密文抛RuntimeException")
    void aesEncryptUtil_decrypt_invalidCiphertext_throws() {
        String invalid = Base64.getEncoder().encodeToString("not-real-encrypted-data".getBytes());
        assertThrows(RuntimeException.class, () -> AesEncryptUtil.decrypt(invalid));
    }

    @Test
    @DisplayName("AesEncryptUtil: decrypt 乱码密文抛RuntimeException")
    void aesEncryptUtil_decrypt_garbageCiphertext_throws() {
        assertThrows(RuntimeException.class, () -> AesEncryptUtil.decrypt("!!!invalid-base64!!"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskMobile 恰好7位正确脱敏")
    void aesEncryptUtil_maskMobile_exact7Chars() {
        String input = "1381234";
        String result = AesEncryptUtil.maskMobile(input);
        assertEquals("138****1234", result);
    }

    @Test
    @DisplayName("AesEncryptUtil: maskMobile 恰好6位返回原字符串")
    void aesEncryptUtil_maskMobile_exact6Chars() {
        String input = "138123";
        String result = AesEncryptUtil.maskMobile(input);
        assertEquals(input, result);
    }

    @Test
    @DisplayName("AesEncryptUtil: maskMobile 8位以上正确脱敏")
    void aesEncryptUtil_maskMobile_8Chars() {
        String input = "1381234567";
        String result = AesEncryptUtil.maskMobile(input);
        assertEquals("138****4567", result);
    }

    @Test
    @DisplayName("AesEncryptUtil: maskIdCard 恰好10位正确脱敏")
    void aesEncryptUtil_maskIdCard_exact10Chars() {
        String input = "1234567890";
        String result = AesEncryptUtil.maskIdCard(input);
        assertEquals("123***7890", result);
    }

    @Test
    @DisplayName("AesEncryptUtil: maskIdCard 11位正确脱敏")
    void aesEncryptUtil_maskIdCard_11Chars() {
        String input = "12345678901";
        String result = AesEncryptUtil.maskIdCard(input);
        assertEquals("123****8901", result);
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 单字返回原字")
    void aesEncryptUtil_maskName_singleChar() {
        assertEquals("张", AesEncryptUtil.maskName("张"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 双字正确脱敏")
    void aesEncryptUtil_maskName_twoChars() {
        assertEquals("张*", AesEncryptUtil.maskName("张三"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 四字正确脱敏中间两个字")
    void aesEncryptUtil_maskName_fourChars() {
        assertEquals("张**丰", AesEncryptUtil.maskName("张三四丰"));
    }

    @Test
    @DisplayName("AesEncryptUtil: encrypt 特殊字符加解密正确")
    void aesEncryptUtil_encryptDecrypt_specialChars() {
        String plain = "!@#$%^&*()_+-=[]{}|;':\",./<>?\n\t\r";
        String encrypted = AesEncryptUtil.encrypt(plain);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("AesEncryptUtil: encrypt 手机号脱敏组合验证")
    void aesEncryptUtil_encryptMobile_roundTripWithMask() {
        String mobile = "13812345678";
        String encrypted = AesEncryptUtil.encrypt(mobile);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(mobile, decrypted);
        String masked = AesEncryptUtil.maskMobile(mobile);
        assertEquals("138****5678", masked);
    }

    // ==================== SnowflakeIdUtil 测试 ====================

    @Test
    @DisplayName("SnowflakeIdUtil: 时间回退场景抛RuntimeException")
    void snowflakeIdUtil_clockRollback_throwsException() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(1L, 1L);

        Field lastTsField = SnowflakeIdUtil.class.getDeclaredField("lastTimestamp");
        lastTsField.setAccessible(true);
        AtomicLong lastTs = (AtomicLong) lastTsField.get(idGen);

        long futureTs = System.currentTimeMillis() + 10000;
        lastTs.set(futureTs);

        RuntimeException ex = assertThrows(RuntimeException.class, idGen::nextId);
        assertTrue(ex.getMessage().contains("Clock moved backwards"));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 序列溢出后等待下一毫秒生成ID")
    void snowflakeIdUtil_sequenceOverflow_tilNextMillis() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(1L, 1L);

        Field seqField = SnowflakeIdUtil.class.getDeclaredField("sequence");
        seqField.setAccessible(true);
        AtomicLong seq = (AtomicLong) seqField.get(idGen);

        Field lastTsField = SnowflakeIdUtil.class.getDeclaredField("lastTimestamp");
        lastTsField.setAccessible(true);
        AtomicLong lastTs = (AtomicLong) lastTsField.get(idGen);

        long now = System.currentTimeMillis();
        lastTs.set(now);
        seq.set(4095L);

        long id = idGen.nextId();
        assertTrue(id > 0);

        long id2 = idGen.nextId();
        assertTrue(id2 > 0);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 连续生成ID不重复(跨毫秒)")
    void snowflakeIdUtil_sameMillisecond_noDuplicates() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(1L, 1L);

        Set<Long> ids = new HashSet<>();
        int count = 5000;
        for (int i = 0; i < count; i++) {
            long id = idGen.nextId();
            assertTrue(id > 0, "ID must be positive");
            ids.add(id);
        }
        // 验证无重复 ID
        assertEquals(count, ids.size(), "All " + count + " IDs should be unique, but got " + ids.size());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 不同实例workerId/datacenterId独立")
    void snowflakeIdUtil_differentInstances_independent() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);

        SnowflakeIdUtil gen1 = ctor.newInstance(0L, 0L);
        SnowflakeIdUtil gen2 = ctor.newInstance(31L, 31L);

        long id1 = gen1.nextId();
        long id2 = gen2.nextId();
        long id3 = gen1.nextId();
        long id4 = gen2.nextId();

        assertNotEquals(id1, id2);
        assertNotEquals(id3, id4);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id4);
    }

    // ==================== LogSanitizer 测试 ====================

    @Test
    @DisplayName("LogSanitizer: sanitize 纯空格输入返回空字符串")
    void logSanitizer_sanitize_whitespaceOnly() {
        String result = LogSanitizer.sanitize("   ");
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 空字符串返回空字符串")
    void logSanitizer_sanitize_emptyString() {
        String result = LogSanitizer.sanitize("");
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 未闭合HTML标签安全处理")
    void logSanitizer_sanitize_unclosedTag() {
        String result = LogSanitizer.sanitize("<div style='color:red'>text without closing");
        assertNotNull(result);
        assertFalse(result.contains("<"));
        assertTrue(result.contains("text without closing"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 混合换行和HTML标签完整清除")
    void logSanitizer_sanitize_mixedNewlinesAndTags() {
        String result = LogSanitizer.sanitize("line1<script>alert(1)</script>\r\nline2<div>text</div>");
        assertFalse(result.contains("\r"));
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
        assertTrue(result.contains("text"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 单个回车符替换")
    void logSanitizer_sanitize_singleCarriageReturn() {
        String result = LogSanitizer.sanitize("hello\rworld");
        assertFalse(result.contains("\r"));
        assertTrue(result.contains("hello"));
        assertTrue(result.contains("world"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 仅回车无换行符替换")
    void logSanitizer_sanitize_crOnly() {
        String result = LogSanitizer.sanitize("a\rb\rc");
        assertFalse(result.contains("\r"));
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    // ==================== ShardingRouter 测试 ====================

    @Test
    @DisplayName("ShardingRouter: 负数userId库索引正确取模")
    void shardingRouter_negativeUserId_dbIndexCorrect() {
        int dbIndex = ShardingRouter.getDbIndex(-1L);
        int tableIndex = ShardingRouter.getTableIndex(-1L);
        assertTrue(dbIndex >= 0);
        assertTrue(tableIndex <= 3);
    }

    @Test
    @DisplayName("ShardingRouter: userId为0路由到库0表0")
    void shardingRouter_zeroUserId_routesToDb0Table0() {
        assertEquals(0, ShardingRouter.getDbIndex(0L));
        assertEquals(0, ShardingRouter.getTableIndex(0L));
    }

    @Test
    @DisplayName("ShardingRouter: userId为31路由正确")
    void shardingRouter_userId31_routesCorrectly() {
        assertEquals(7, ShardingRouter.getDbIndex(31L));
        assertEquals(3, ShardingRouter.getTableIndex(31L));
    }

    @Test
    @DisplayName("ShardingRouter: userId为32跨库路由")
    void shardingRouter_userId32_crossDb() {
        assertEquals(0, ShardingRouter.getDbIndex(32L));
        assertEquals(0, ShardingRouter.getTableIndex(32L));
    }

    @Test
    @DisplayName("ShardingRouter: userId为64跨库路由")
    void shardingRouter_userId64_crossDb() {
        assertEquals(0, ShardingRouter.getDbIndex(64L));
        assertEquals(0, ShardingRouter.getTableIndex(64L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbName正确拼接库名")
    void shardingRouter_getDbName_concatenatesCorrectly() {
        assertEquals("order_0", ShardingRouter.getDbName(0L, "order"));
        assertEquals("order_7", ShardingRouter.getDbName(31L, "order"));
    }

    @Test
    @DisplayName("ShardingRouter: getTableName正确拼接表名")
    void shardingRouter_getTableName_concatenatesCorrectly() {
        assertEquals("order_0", ShardingRouter.getTableName(0L, "order"));
        assertEquals("order_3", ShardingRouter.getTableName(31L, "order"));
    }

    @Test
    @DisplayName("ShardingRouter: 相邻userId不同表路由")
    void shardingRouter_adjacentUserId_differentTables() {
        int table0 = ShardingRouter.getTableIndex(0L);
        int table1 = ShardingRouter.getTableIndex(1L);
        assertNotEquals(table0, table1);
    }

    // ==================== DistributedLock 测试 ====================

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("DistributedLock: tryLock返回false时抛获取锁失败异常")
    void distributedLock_tryLockFail_throwsException() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }

        DistributedLock lock = new DistributedLock(redissonClient);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> lock.executeWithLock("failKey", () -> "test"));
        assertTrue(ex.getMessage().contains("获取分布式锁失败"));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    @DisplayName("DistributedLock: executeWithMultiLock 相同ID仍正确加锁")
    void distributedLock_executeWithMultiLock_sameIds() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        String result = lock.executeWithMultiLock(100L, 100L, () -> "same");
        assertEquals("same", result);
        verify(redissonClient, times(2)).getLock("lsc:lock:user:100");
    }

    @Test
    @DisplayName("DistributedLock: executeWithMultiLock 内层锁获取失败")
    void distributedLock_executeWithMultiLock_innerLockFail() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true)
                    .thenReturn(false);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        DistributedLock lock = new DistributedLock(redissonClient);
        assertThrows(RuntimeException.class,
                () -> lock.executeWithMultiLock(100L, 200L, () -> "test"));
    }

    @Test
    @DisplayName("DistributedLock: executeWithLock 业务异常时仍释放锁")
    void distributedLock_businessException_lockReleased() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        assertThrows(IllegalStateException.class,
                () -> lock.executeWithLock("exKey", () -> {
                    throw new IllegalStateException("业务异常");
                }));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("DistributedLock: executeWithLock 默认锁key前缀")
    void distributedLock_defaultLockKey_prefixCorrect() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        lock.executeWithLock("myKey", () -> null);
        verify(redissonClient).getLock("lsc:lock:myKey");
    }

    @Test
    @DisplayName("DistributedLock: executeWithLock 自定义参数传递正确")
    void distributedLock_customParams_passedCorrectly() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(eq(10L), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        lock.executeWithLock("custom", 10L, 60L, () -> null);
        try {
            verify(rLock).tryLock(10L, 60L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
    }
}