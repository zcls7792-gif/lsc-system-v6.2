package com.lianshengtong.common.utils;

import com.lianshengtong.common.idempotent.IdempotentKeyGenerator;
import com.lianshengtong.common.sharding.ShardingRouter;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("工具类边界用例测试")
class UtilsEdgeCaseTest {

    @Nested
    @DisplayName("ReleaseCalcUtil 测试")
    class ReleaseCalcUtilTests {

        private static final BigDecimal RATE_MAX = new BigDecimal("0.0005");
        private static final BigDecimal RATE_MIN = new BigDecimal("0.0003");
        private static final BigDecimal K_MIN = new BigDecimal("0.0050");
        private static final BigDecimal K_MAX = new BigDecimal("0.0100");
        private static final BigDecimal ALPHA = new BigDecimal("0.05");

        @Test
        @DisplayName("calcRate: k <= kMin 时返回 rateMax")
        void calcRate_kBelowMin_returnsRateMax() {
            BigDecimal k = new BigDecimal("0.0049");
            BigDecimal rate = ReleaseCalcUtil.calcRate(k, RATE_MAX, RATE_MIN, K_MIN, K_MAX, ALPHA);
            assertEquals(0, RATE_MAX.compareTo(rate));
        }

        @Test
        @DisplayName("calcRate: k == kMin 时返回 rateMax")
        void calcRate_kEqualsMin_returnsRateMax() {
            BigDecimal k = new BigDecimal("0.0050");
            BigDecimal rate = ReleaseCalcUtil.calcRate(k, RATE_MAX, RATE_MIN, K_MIN, K_MAX, ALPHA);
            assertEquals(0, RATE_MAX.compareTo(rate));
        }

        @Test
        @DisplayName("calcRate: k >= kMax 时返回 rateMin")
        void calcRate_kAboveMax_returnsRateMin() {
            BigDecimal k = new BigDecimal("0.0101");
            BigDecimal rate = ReleaseCalcUtil.calcRate(k, RATE_MAX, RATE_MIN, K_MIN, K_MAX, ALPHA);
            assertEquals(0, RATE_MIN.compareTo(rate));
        }

        @Test
        @DisplayName("calcRate: k == kMax 时返回 rateMin")
        void calcRate_kEqualsMax_returnsRateMin() {
            BigDecimal k = new BigDecimal("0.0100");
            BigDecimal rate = ReleaseCalcUtil.calcRate(k, RATE_MAX, RATE_MIN, K_MIN, K_MAX, ALPHA);
            assertEquals(0, RATE_MIN.compareTo(rate));
        }

        @Test
        @DisplayName("calcRate: 中间区 k=0.0075 -> rate=0.000375")
        void calcRate_middleZone_correctCalc() {
            BigDecimal k = new BigDecimal("0.0075");
            BigDecimal rate = ReleaseCalcUtil.calcRate(k, RATE_MAX, RATE_MIN, K_MIN, K_MAX, ALPHA);
            BigDecimal expected = new BigDecimal("0.000375").setScale(6, java.math.RoundingMode.HALF_UP);
            assertEquals(0, expected.compareTo(rate));
        }

        @Test
        @DisplayName("calcRate: 边界值 rate 保留6位小数")
        void calcRate_scale6() {
            BigDecimal k = new BigDecimal("0.0075");
            BigDecimal rate = ReleaseCalcUtil.calcRate(k, RATE_MAX, RATE_MIN, K_MIN, K_MAX, ALPHA);
            assertEquals(6, rate.scale());
        }

        @Test
        @DisplayName("calcReleaseAmount: 正常向下取整")
        void calcReleaseAmount_normalFloor() {
            BigDecimal rate = new BigDecimal("0.0004");
            long result = ReleaseCalcUtil.calcReleaseAmount(1_000_000L, rate);
            assertEquals(400L, result);
        }

        @Test
        @DisplayName("calcReleaseAmount: 小数部分截断")
        void calcReleaseAmount_floorTruncation() {
            BigDecimal rate = new BigDecimal("0.0003");
            long result = ReleaseCalcUtil.calcReleaseAmount(1_234_567L, rate);
            long expected = BigDecimal.valueOf(1_234_567L)
                    .multiply(rate)
                    .setScale(0, java.math.RoundingMode.DOWN)
                    .longValue();
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("calcReleaseAmount: lLocked为0返回0")
        void calcReleaseAmount_zeroLocked() {
            BigDecimal rate = new BigDecimal("0.0005");
            assertEquals(0L, ReleaseCalcUtil.calcReleaseAmount(0L, rate));
        }

        @Test
        @DisplayName("calcReleaseAmount: lLocked为负数返回0")
        void calcReleaseAmount_negativeLocked() {
            BigDecimal rate = new BigDecimal("0.0005");
            assertEquals(0L, ReleaseCalcUtil.calcReleaseAmount(-100L, rate));
        }

        @Test
        @DisplayName("percent: 百分号字符串转换")
        void percent_stringWithPercent() {
            BigDecimal result = ReleaseCalcUtil.percent("0.05%");
            assertEquals(0, new BigDecimal("0.0005").compareTo(result));
        }

        @Test
        @DisplayName("percent: 无百分号字符串直接转BigDecimal")
        void percent_plainNumber() {
            BigDecimal result = ReleaseCalcUtil.percent("0.0005");
            assertEquals(0, new BigDecimal("0.0005").compareTo(result));
        }

        @Test
        @DisplayName("percent: null输入返回ZERO")
        void percent_null_returnsZero() {
            assertEquals(0, BigDecimal.ZERO.compareTo(ReleaseCalcUtil.percent(null)));
        }

        @Test
        @DisplayName("percent: 空字符串返回ZERO")
        void percent_empty_returnsZero() {
            assertEquals(0, BigDecimal.ZERO.compareTo(ReleaseCalcUtil.percent("")));
        }

        @Test
        @DisplayName("percent: 带空格的百分号字符串")
        void percent_whitespaceTrimming() {
            BigDecimal result = ReleaseCalcUtil.percent(" 0.03% ");
            assertEquals(0, new BigDecimal("0.0003").compareTo(result));
        }

        @Test
        @DisplayName("isRateValid: 范围内返回true")
        void isRateValid_inRange_true() {
            assertTrue(ReleaseCalcUtil.isRateValid(
                    new BigDecimal("0.0004"), RATE_MIN, RATE_MAX));
        }

        @Test
        @DisplayName("isRateValid: 等于边界值返回true")
        void isRateValid_atBoundary_true() {
            assertTrue(ReleaseCalcUtil.isRateValid(RATE_MIN, RATE_MIN, RATE_MAX));
            assertTrue(ReleaseCalcUtil.isRateValid(RATE_MAX, RATE_MIN, RATE_MAX));
        }

        @Test
        @DisplayName("isRateValid: 低于下限返回false")
        void isRateValid_belowMin_false() {
            assertFalse(ReleaseCalcUtil.isRateValid(
                    new BigDecimal("0.0002"), RATE_MIN, RATE_MAX));
        }

        @Test
        @DisplayName("isRateValid: 高于上限返回false")
        void isRateValid_aboveMax_false() {
            assertFalse(ReleaseCalcUtil.isRateValid(
                    new BigDecimal("0.0006"), RATE_MIN, RATE_MAX));
        }

        @Test
        @DisplayName("SCALE常量值为6")
        void scale_constantIs6() {
            assertEquals(6, ReleaseCalcUtil.SCALE);
        }
    }

    @Nested
    @DisplayName("IdempotentKeyGenerator 测试")
    class IdempotentKeyGeneratorTests {

        @Test
        @DisplayName("generate: 正常生成幂等键")
        void generate_success() {
            String key = IdempotentKeyGenerator.generate("ORDER_CREATE", 1001L);
            assertNotNull(key);
            assertTrue(key.startsWith("ORDER_CREATE_1001_"));
            String[] parts = key.split("_");
            assertEquals(4, parts.length);
        }

        @Test
        @DisplayName("generate: 空白bizType抛异常")
        void generate_blankBizType_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> IdempotentKeyGenerator.generate("", 1L));
            assertThrows(IllegalArgumentException.class,
                    () -> IdempotentKeyGenerator.generate("  ", 1L));
        }

        @Test
        @DisplayName("generate: null bizType抛异常")
        void generate_nullBizType_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> IdempotentKeyGenerator.generate(null, 1L));
        }

        @Test
        @DisplayName("generate: null userId抛异常")
        void generate_nullUserId_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> IdempotentKeyGenerator.generate("ORDER", null));
        }

        @Test
        @DisplayName("generate: 相同参数生成不同幂等键(含随机数)")
        void generate_uniqueKeys() {
            String k1 = IdempotentKeyGenerator.generate("BIZ", 1L);
            String k2 = IdempotentKeyGenerator.generate("BIZ", 1L);
            assertNotEquals(k1, k2);
        }

        @Test
        @DisplayName("generate: 时间戳格式为yyyyMMddHHmmssSSS")
        void generate_timestampFormat() {
            String key = IdempotentKeyGenerator.generate("T", 1L);
            String[] parts = key.split("_");
            String timestamp = parts[2];
            assertEquals(17, timestamp.length());
        }

        @Test
        @DisplayName("generateSystem: 系统级幂等键生成")
        void generateSystem_success() {
            String key = IdempotentKeyGenerator.generateSystem("DAILY_RELEASE");
            assertNotNull(key);
            assertTrue(key.startsWith("SYS_DAILY_RELEASE_"));
            String[] parts = key.split("_");
            assertEquals(4, parts.length);
        }

        @Test
        @DisplayName("generateSystem: 空白bizType抛异常")
        void generateSystem_blankBizType_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> IdempotentKeyGenerator.generateSystem(""));
        }

        @Test
        @DisplayName("generateSystem: null bizType抛异常")
        void generateSystem_nullBizType_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> IdempotentKeyGenerator.generateSystem(null));
        }

        @Test
        @DisplayName("generateSystem: 相同参数生成不同幂等键")
        void generateSystem_uniqueKeys() {
            String k1 = IdempotentKeyGenerator.generateSystem("SYS");
            String k2 = IdempotentKeyGenerator.generateSystem("SYS");
            assertNotEquals(k1, k2);
        }
    }

    @Nested
    @DisplayName("ShardingRouter 测试")
    class ShardingRouterTests {

        @Test
        @DisplayName("getDbIndex: 低user路由到库0")
        void getDbIndex_lowUser_db0() {
            assertEquals(0, ShardingRouter.getDbIndex(1L));
            assertEquals(0, ShardingRouter.getDbIndex(31L));
        }

        @Test
        @DisplayName("getDbIndex: user=32路由到库1")
        void getDbIndex_user32_db1() {
            assertEquals(1, ShardingRouter.getDbIndex(32L));
            assertEquals(1, ShardingRouter.getDbIndex(63L));
        }

        @Test
        @DisplayName("getDbIndex: 跨库边界值测试")
        void getDbIndex_boundaryValues() {
            assertEquals(0, ShardingRouter.getDbIndex(0L));
            assertEquals(0, ShardingRouter.getDbIndex(31L));
            assertEquals(1, ShardingRouter.getDbIndex(32L));
            assertEquals(1, ShardingRouter.getDbIndex(63L));
            assertEquals(2, ShardingRouter.getDbIndex(64L));
            assertEquals(7, ShardingRouter.getDbIndex(255L));
            assertEquals(0, ShardingRouter.getDbIndex(256L));
        }

        @Test
        @DisplayName("getTableIndex: 低user路由到表")
        void getTableIndex_lowUser() {
            assertEquals(1, ShardingRouter.getTableIndex(1L));
            assertEquals(3, ShardingRouter.getTableIndex(3L));
        }

        @Test
        @DisplayName("getTableIndex: 边界值为0")
        void getTableIndex_boundaryZero() {
            assertEquals(0, ShardingRouter.getTableIndex(0L));
            assertEquals(0, ShardingRouter.getTableIndex(4L));
        }

        @Test
        @DisplayName("getTableIndex: 表索引范围0-3")
        void getTableIndex_range0to3() {
            for (long i = 0; i < 32; i++) {
                int tableIdx = ShardingRouter.getTableIndex(i);
                assertTrue(tableIdx >= 0 && tableIdx <= 3,
                        "user " + i + " table index out of range: " + tableIdx);
            }
        }

        @Test
        @DisplayName("getDbName: 正确拼接库名")
        void getDbName_concatenation() {
            assertEquals("order_0", ShardingRouter.getDbName(1L, "order"));
            assertEquals("order_1", ShardingRouter.getDbName(32L, "order"));
        }

        @Test
        @DisplayName("getTableName: 正确拼接表名")
        void getTableName_concatenation() {
            assertEquals("user_1", ShardingRouter.getTableName(1L, "user"));
            assertEquals("user_0", ShardingRouter.getTableName(4L, "user"));
        }

        @Test
        @DisplayName("常量验证: SHARDING_COUNT=32, DB_COUNT=8, TABLES_PER_DB=4")
        void constants_verify() {
            assertEquals(32, ShardingRouter.SHARDING_COUNT);
            assertEquals(8, ShardingRouter.DB_COUNT);
            assertEquals(4, ShardingRouter.TABLES_PER_DB);
        }

        @Test
        @DisplayName("分片路由: 负数userId正确处理取模")
        void negativeUserId_correctMod() {
            int dbIdx = ShardingRouter.getDbIndex(-1L);
            int tableIdx = ShardingRouter.getTableIndex(-1L);
            assertTrue(dbIdx >= 0 && dbIdx <= 7);
            assertTrue(tableIdx >= 0 && tableIdx <= 3);
        }

        @Test
        @DisplayName("getDbIndex: userId=2^63-1(最大值)不溢出")
        void maxUserId_noOverflow() {
            int dbIdx = ShardingRouter.getDbIndex(Long.MAX_VALUE);
            int tableIdx = ShardingRouter.getTableIndex(Long.MAX_VALUE);
            assertTrue(dbIdx >= 0 && dbIdx <= 7);
            assertTrue(tableIdx >= 0 && tableIdx <= 3);
        }
    }
}