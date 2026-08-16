package com.lianshengtong.common.util;

import com.lianshengtong.common.idempotent.IdempotentKeyGenerator;
import com.lianshengtong.common.lock.DistributedLock;
import com.lianshengtong.common.security.AdminRoleAspect;
import com.lianshengtong.common.utils.AesEncryptUtil;
import com.lianshengtong.common.utils.ReleaseCalcUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LSC Common P1 核心类单元测试")
@ExtendWith(MockitoExtension.class)
class CommonP1Test {

    // ==================== AesEncryptUtil 测试 ====================

    @Test
    @DisplayName("AesEncryptUtil: encrypt/decrypt 往返加解密正确")
    void aesEncryptUtil_encryptDecrypt_roundTrip() {
        String plain = "张三13812345678";
        String encrypted = AesEncryptUtil.encrypt(plain);

        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);

        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("AesEncryptUtil: encrypt null输入返回null")
    void aesEncryptUtil_encryptNull_returnsNull() {
        assertNull(AesEncryptUtil.encrypt(null));
    }

    @Test
    @DisplayName("AesEncryptUtil: decrypt null输入返回null")
    void aesEncryptUtil_decryptNull_returnsNull() {
        assertNull(AesEncryptUtil.decrypt(null));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskMobile 正常脱敏")
    void aesEncryptUtil_maskMobile_normal() {
        assertEquals("138****5678", AesEncryptUtil.maskMobile("13812345678"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskMobile null和短输入处理")
    void aesEncryptUtil_maskMobile_nullAndShort() {
        assertNull(AesEncryptUtil.maskMobile(null));
        assertEquals("138", AesEncryptUtil.maskMobile("138"));
        assertEquals("138123", AesEncryptUtil.maskMobile("138123"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskIdCard 正常脱敏")
    void aesEncryptUtil_maskIdCard_normal() {
        assertEquals("110***********1234", AesEncryptUtil.maskIdCard("110101199001011234"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskIdCard null和短输入处理")
    void aesEncryptUtil_maskIdCard_nullAndShort() {
        assertNull(AesEncryptUtil.maskIdCard(null));
        assertEquals("1101", AesEncryptUtil.maskIdCard("1101"));
        assertEquals("123456789", AesEncryptUtil.maskIdCard("123456789"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 两字姓名脱敏")
    void aesEncryptUtil_maskName_twoChars() {
        assertEquals("张*", AesEncryptUtil.maskName("张三"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 三字姓名脱敏")
    void aesEncryptUtil_maskName_threeChars() {
        assertEquals("张*丰", AesEncryptUtil.maskName("张三丰"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 四字及以上姓名脱敏")
    void aesEncryptUtil_maskName_fourChars() {
        assertEquals("张**丰", AesEncryptUtil.maskName("张三四丰"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 单字和null处理")
    void aesEncryptUtil_maskName_singleCharAndNull() {
        assertNull(AesEncryptUtil.maskName(null));
        assertEquals("张", AesEncryptUtil.maskName("张"));
    }

    // ==================== DistributedLock 测试 ====================

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("DistributedLock: 获取锁成功并执行操作")
    void distributedLock_executeWithLock_success() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        String result = lock.executeWithLock("testKey", () -> "hello");

        assertEquals("hello", result);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("DistributedLock: 获取锁失败抛出RuntimeException")
    void distributedLock_executeWithLock_failToAcquire() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }

        DistributedLock lock = new DistributedLock(redissonClient);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> lock.executeWithLock("failKey", () -> "hello"));

        assertTrue(ex.getMessage().contains("获取分布式锁失败"));
        assertTrue(ex.getMessage().contains("failKey"));
    }

    @Test
    @DisplayName("DistributedLock: 锁被中断时恢复中断标志并抛异常")
    void distributedLock_executeWithLock_interrupted() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("中断测试"));

        DistributedLock lock = new DistributedLock(redissonClient);

        try {
            lock.executeWithLock("interruptKey", () -> "hello");
            fail("期望抛出RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("获取锁被中断"));
            assertTrue(ex.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted(), "中断标志应被恢复");
        }
    }

    @Test
    @DisplayName("DistributedLock: 多锁按ID排序加锁防止死锁")
    void distributedLock_executeWithMultiLock_sortedIds() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);

        String result = lock.executeWithMultiLock(100L, 200L, () -> "multiLock");

        assertEquals("multiLock", result);
        verify(redissonClient).getLock("lsc:lock:user:100");
        verify(redissonClient).getLock("lsc:lock:user:200");
    }

    @Test
    @DisplayName("DistributedLock: 多锁ID逆序传入时仍按正确顺序加锁")
    void distributedLock_executeWithMultiLock_reverseIds() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);

        lock.executeWithMultiLock(500L, 200L, () -> {
            return null;
        });

        verify(redissonClient).getLock("lsc:lock:user:200");
        verify(redissonClient).getLock("lsc:lock:user:500");
    }

    @Test
    @DisplayName("DistributedLock: Runnable版本正确执行")
    void distributedLock_executeWithLock_runnable() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        StringBuilder sb = new StringBuilder();

        lock.executeWithLock("runnableKey", () -> sb.append("executed"));

        assertEquals("executed", sb.toString());
    }

    @Test
    @DisplayName("DistributedLock: 加锁后finally块释放锁")
    void distributedLock_executeWithLock_finallyUnlock() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);

        lock.executeWithLock("unlockKey", () -> "test");

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("DistributedLock: 锁未被当前线程持有时不释放")
    void distributedLock_executeWithLock_notHeldByThread() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        DistributedLock lock = new DistributedLock(redissonClient);

        lock.executeWithLock("notHeldKey", () -> "test");

        verify(rLock, never()).unlock();
    }

    // ==================== ReleaseCalcUtil 测试 ====================

    @Test
    @DisplayName("ReleaseCalcUtil: calcRate k低于kMin返回rateMax")
    void releaseCalcUtil_calcRate_belowMin() {
        BigDecimal k = new BigDecimal("0.003");
        BigDecimal rateMax = new BigDecimal("0.00050");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal kMin = new BigDecimal("0.0050");
        BigDecimal kMax = new BigDecimal("0.0100");
        BigDecimal alpha = new BigDecimal("0.05");

        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);

        assertEquals(0, rate.compareTo(rateMax));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcRate k高于kMax返回rateMin")
    void releaseCalcUtil_calcRate_aboveMax() {
        BigDecimal k = new BigDecimal("0.0200");
        BigDecimal rateMax = new BigDecimal("0.00050");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal kMin = new BigDecimal("0.0050");
        BigDecimal kMax = new BigDecimal("0.0100");
        BigDecimal alpha = new BigDecimal("0.05");

        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);

        assertEquals(0, rate.compareTo(rateMin));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcRate k在区间内按公式计算")
    void releaseCalcUtil_calcRate_betweenRange() {
        BigDecimal k = new BigDecimal("0.0075");
        BigDecimal rateMax = new BigDecimal("0.00050");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal kMin = new BigDecimal("0.0050");
        BigDecimal kMax = new BigDecimal("0.0100");
        BigDecimal alpha = new BigDecimal("0.05");

        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);

        BigDecimal expected = new BigDecimal("0.00075")
                .subtract(alpha.multiply(k))
                .setScale(6, java.math.RoundingMode.HALF_UP);
        assertEquals(0, rate.compareTo(expected));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcRate k等于kMin返回rateMax")
    void releaseCalcUtil_calcRate_atMinBoundary() {
        BigDecimal k = new BigDecimal("0.0050");
        BigDecimal rateMax = new BigDecimal("0.00050");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal kMin = new BigDecimal("0.0050");
        BigDecimal kMax = new BigDecimal("0.0100");
        BigDecimal alpha = new BigDecimal("0.05");

        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);

        assertEquals(0, rate.compareTo(rateMax));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcRate k等于kMax返回rateMin")
    void releaseCalcUtil_calcRate_atMaxBoundary() {
        BigDecimal k = new BigDecimal("0.0100");
        BigDecimal rateMax = new BigDecimal("0.00050");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal kMin = new BigDecimal("0.0050");
        BigDecimal kMax = new BigDecimal("0.0100");
        BigDecimal alpha = new BigDecimal("0.05");

        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);

        assertEquals(0, rate.compareTo(rateMin));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcReleaseAmount 正常计算向下取整")
    void releaseCalcUtil_calcReleaseAmount_normal() {
        long lLocked = 1_000_000L;
        BigDecimal rate = new BigDecimal("0.00075");

        long result = ReleaseCalcUtil.calcReleaseAmount(lLocked, rate);

        assertEquals(750L, result);
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcReleaseAmount 小数结果向下取整")
    void releaseCalcUtil_calcReleaseAmount_fractional() {
        long lLocked = 1_000_000L;
        BigDecimal rate = new BigDecimal("0.000749");

        long result = ReleaseCalcUtil.calcReleaseAmount(lLocked, rate);

        assertEquals(749L, result);
    }

    @Test
    @DisplayName("ReleaseCalcUtil: calcReleaseAmount 零值计算")
    void releaseCalcUtil_calcReleaseAmount_zero() {
        long result = ReleaseCalcUtil.calcReleaseAmount(0L, new BigDecimal("0.00075"));
        assertEquals(0L, result);

        result = ReleaseCalcUtil.calcReleaseAmount(1000L, BigDecimal.ZERO);
        assertEquals(0L, result);
    }

    @Test
    @DisplayName("ReleaseCalcUtil: percent 带百分号转换")
    void releaseCalcUtil_percent_withPercentSign() {
        BigDecimal result = ReleaseCalcUtil.percent("0.05%");
        assertEquals(0, result.compareTo(new BigDecimal("0.000500")));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: percent 不带百分号直接解析")
    void releaseCalcUtil_percent_withoutPercentSign() {
        BigDecimal result = ReleaseCalcUtil.percent("1.5");
        assertEquals(0, result.compareTo(new BigDecimal("1.500000")));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: percent null和空字符串返回0")
    void releaseCalcUtil_percent_nullAndEmpty() {
        assertEquals(0, ReleaseCalcUtil.percent(null).compareTo(BigDecimal.ZERO));
        assertEquals(0, ReleaseCalcUtil.percent("").compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: percent 带空格的百分号字符串")
    void releaseCalcUtil_percent_withSpaces() {
        BigDecimal result = ReleaseCalcUtil.percent(" 1.5% ");
        assertEquals(0, result.compareTo(new BigDecimal("0.015000")));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: isRateValid 在有效范围内")
    void releaseCalcUtil_isRateValid_inRange() {
        BigDecimal rate = new BigDecimal("0.00040");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal rateMax = new BigDecimal("0.00050");

        assertTrue(ReleaseCalcUtil.isRateValid(rate, rateMin, rateMax));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: isRateValid 超出范围返回false")
    void releaseCalcUtil_isRateValid_outOfRange() {
        BigDecimal rate = new BigDecimal("0.00020");
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal rateMax = new BigDecimal("0.00050");

        assertFalse(ReleaseCalcUtil.isRateValid(rate, rateMin, rateMax));

        rate = new BigDecimal("0.00060");
        assertFalse(ReleaseCalcUtil.isRateValid(rate, rateMin, rateMax));
    }

    @Test
    @DisplayName("ReleaseCalcUtil: isRateValid 边界值有效")
    void releaseCalcUtil_isRateValid_boundary() {
        BigDecimal rateMin = new BigDecimal("0.00030");
        BigDecimal rateMax = new BigDecimal("0.00050");

        assertTrue(ReleaseCalcUtil.isRateValid(rateMin, rateMin, rateMax));
        assertTrue(ReleaseCalcUtil.isRateValid(rateMax, rateMin, rateMax));
    }

    // ==================== IdempotentKeyGenerator 测试 ====================

    @Test
    @DisplayName("IdempotentKeyGenerator: generate 正常生成格式")
    void idempotentKeyGenerator_generate_normal() {
        String key = IdempotentKeyGenerator.generate("ORDER", 12345L);

        assertNotNull(key);
        assertTrue(key.startsWith("ORDER_12345_"));
        assertTrue(key.matches("ORDER_12345_\\d{17}_\\d{4}"));
    }

    @Test
    @DisplayName("IdempotentKeyGenerator: generate 空白bizType抛异常")
    void idempotentKeyGenerator_generate_blankBizType() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotentKeyGenerator.generate("", 123L));
        assertThrows(IllegalArgumentException.class,
                () -> IdempotentKeyGenerator.generate("  ", 123L));
    }

    @Test
    @DisplayName("IdempotentKeyGenerator: generate null userId抛异常")
    void idempotentKeyGenerator_generate_nullUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotentKeyGenerator.generate("ORDER", null));
    }

    @Test
    @DisplayName("IdempotentKeyGenerator: generateSystem 正常生成格式")
    void idempotentKeyGenerator_generateSystem_normal() {
        String key = IdempotentKeyGenerator.generateSystem("TASK");

        assertNotNull(key);
        assertTrue(key.startsWith("SYS_TASK_"));
        assertTrue(key.matches("SYS_TASK_\\d{17}_\\d{4}"));
    }

    @Test
    @DisplayName("IdempotentKeyGenerator: generateSystem 空白bizType抛异常")
    void idempotentKeyGenerator_generateSystem_blankBizType() {
        assertThrows(IllegalArgumentException.class,
                () -> IdempotentKeyGenerator.generateSystem(""));
        assertThrows(IllegalArgumentException.class,
                () -> IdempotentKeyGenerator.generateSystem(null));
    }

    @Test
    @DisplayName("IdempotentKeyGenerator: generate 每次生成唯一键")
    void idempotentKeyGenerator_generate_uniqueKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            keys.add(IdempotentKeyGenerator.generate("TEST_" + i, (long) i));
        }
        assertEquals(200, keys.size());
    }

    // ==================== AdminRoleAspect 测试 ====================

    @Test
    @DisplayName("AdminRoleAspect: 构造函数和基本字段")
    void adminRoleAspect_constructor_basic() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        assertNotNull(aspect);
        assertNull(aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: 带参构造函数设置request")
    void adminRoleAspect_constructor_withRequest() {
        jakarta.servlet.http.HttpServletRequest mockRequest =
                mock(jakarta.servlet.http.HttpServletRequest.class);
        AdminRoleAspect aspect = new AdminRoleAspect(mockRequest);

        assertNotNull(aspect);
        assertEquals(mockRequest, aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: setRequest/getRequest 正常工作")
    void adminRoleAspect_setGetRequest() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        assertNull(aspect.getRequest());

        jakarta.servlet.http.HttpServletRequest mockRequest =
                mock(jakarta.servlet.http.HttpServletRequest.class);
        aspect.setRequest(mockRequest);
        assertEquals(mockRequest, aspect.getRequest());
    }

    @Test
    @DisplayName("AesEncryptUtil: encrypt/decrypt 与实际生产密文兼容")
    void aesEncryptUtil_encryptDecrypt_largePayload() {
        String longText = "用户实名信息：张三，身份证号110101199001011234，手机号13812345678，地址北京市朝阳区建国路88号";
        String encrypted = AesEncryptUtil.encrypt(longText);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(longText, decrypted);
    }

    @Test
    @DisplayName("DistributedLock: 默认锁前缀常量正确")
    void distributedLock_lockPrefix_verified() {
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
    @DisplayName("ReleaseCalcUtil: calcRate 中间值精确验证")
    void releaseCalcUtil_calcRate_precision() {
        BigDecimal k = new BigDecimal("0.0075");
        BigDecimal rateMax = new BigDecimal("0.000500");
        BigDecimal rateMin = new BigDecimal("0.000300");
        BigDecimal kMin = new BigDecimal("0.005000");
        BigDecimal kMax = new BigDecimal("0.010000");
        BigDecimal alpha = new BigDecimal("0.050000");

        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);

        BigDecimal expected = new BigDecimal("0.000375");
        assertEquals(0, rate.compareTo(expected),
                "rate应精确等于0.000375, 实际: " + rate);
    }
}