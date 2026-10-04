package com.lianshengtong.common.lsc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V7.7.2 核心规则测试（第十五章 T01-T13 算法验证）
 * <p>
 * 覆盖：赠送算法、抵扣上限、释放率插值、日释放余数累计、周转率边界。
 * 全部为纯计算测试，不依赖数据库。
 * </p>
 */
class V772CoreRuleTest {

    // ==================== T01: C价130元成本80元系数0.8 -> 全人民币赠400000 unit ====================
    @Test
    void t01_fullRmbGrant_C130Cost80K08() {
        // 130元 = 13000分，80元 = 8000分，K=0.8=800000ppm
        long grant = GrantCalculator.calcFullRmbGrant(13000L, 8000L, 800000L);
        assertEquals(400000L, grant, "T01: C价130成本80全人民币应赠400000 unit");
        assertEquals("40.0000", LscMathUtil.formatLsc(grant));
    }

    // ==================== T02: 优惠券20元且满足门槛 -> 赠338461 unit ====================
    @Test
    void t02_coupon20_grant338461() {
        // 130元商品，券20元，人民币实付110元=11000分
        long grant = GrantCalculator.calcHybridGrant(13000L, 8000L, 11000L, 800000L);
        assertEquals(338461L, grant, "T02: 券20元后应赠338461 unit，不取整为33 LSC");
        assertEquals("33.8461", LscMathUtil.formatLsc(grant));
    }

    // ==================== T03: B价100元成本80元抵扣20元 -> 赠128000 unit ====================
    @Test
    void t03_b100Cost80Deduct20_grant128000() {
        // B价100元=10000分，成本80元=8000分，抵扣20元，人民币实付80元=8000分
        long grant = GrantCalculator.calcHybridGrant(10000L, 8000L, 8000L, 800000L);
        assertEquals(128000L, grant, "T03: B价100抵扣20应赠128000 unit");
        assertEquals("12.8000", LscMathUtil.formatLsc(grant));
    }

    // ==================== T04: 成本大于售价 -> 赠送0 ====================
    @Test
    void t04_costGreaterThanSale_zeroGrant() {
        // 售价100元，成本120元
        long grant = GrantCalculator.calcFullRmbGrant(10000L, 12000L, 800000L);
        assertEquals(0L, grant, "T04: 成本大于售价应赠送0，不出现负值");
    }

    @Test
    void t04_costEqualsSale_zeroGrant() {
        long grant = GrantCalculator.calcFullRmbGrant(10000L, 10000L, 800000L);
        assertEquals(0L, grant, "T04: 成本等于售价应赠送0");
    }

    // ==================== T05: 申请抵扣101 unit -> 拒绝（非100整数倍）====================
    @Test
    void t05_unitNotMultipleOf100() {
        assertFalse(LscMathUtil.isUnitMultipleOf100(101), "T05: 101 unit 不是100整数倍");
        assertTrue(LscMathUtil.isUnitMultipleOf100(100));
        assertTrue(LscMathUtil.isUnitMultipleOf100(0));
        assertTrue(LscMathUtil.isUnitMultipleOf100(2600)); // 26元 = 2600 unit
    }

    // ==================== T07: 抵扣比例50.01% -> 拒绝（超硬顶）====================
    @Test
    void t07_deductionHardCap() {
        // 50.01% = 500100 ppm，超过 500000 硬顶
        long maxCent = GrantCalculator.calcMaxDeductionCent(10000L, 500100L);
        // calcMaxDeductionCent 内部会 clamp 到 DEDUCTION_MAX_PPM=500000
        long expected = LscMathUtil.mulPpmFloor(10000L, LscUnitConstants.DEDUCTION_MAX_PPM);
        assertEquals(expected, maxCent, "T07: 抵扣比例超硬顶应被clamp到50%");
        assertEquals(5000L, maxCent, "100元商品最多抵扣50元=5000分");
    }

    @Test
    void t07_deductionHardCap_constant() {
        assertEquals(500000L, LscUnitConstants.DEDUCTION_MAX_PPM, "抵扣硬顶为50%=500000ppm");
    }

    // ==================== T08: 多商品最大余数分摊守恒 ====================
    @Test
    void t08_largestRemainderAllocation() {
        // 3个单元，销售金额分别 3333, 3333, 3334 分（合计10000），优惠总额100分
        long[] remainders = {3333, 3333, 3334};
        long[] ids = {1, 2, 3};
        long extra = 1; // 100 - 99 = 1分剩余

        long[] result = LscMathUtil.largestRemainderAllocate(remainders, ids, extra);
        // 余数最大的是 id=3 (3334)，应获得额外1分
        assertEquals(0L, result[0]);
        assertEquals(0L, result[1]);
        assertEquals(1L, result[2]);
    }

    @Test
    void t08_largestRemainderAllocation_tieBreakById() {
        // 余数相同，按id升序
        long[] remainders = {5000, 5000, 5000};
        long[] ids = {3, 1, 2};
        long extra = 2;

        long[] result = LscMathUtil.largestRemainderAllocate(remainders, ids, extra);
        // ids[1]=1 最小，ids[2]=2 次之
        assertEquals(0L, result[0]); // id=3
        assertEquals(1L, result[1]); // id=1
        assertEquals(1L, result[2]); // id=2
    }

    // ==================== T09: w=1%/1.5%/2% -> rate=0.05%/0.075%/0.10% ====================
    @Test
    void t09_rateInterpolation() {
        long wMin = LscUnitConstants.W_MIN_DEFAULT_PPM; // 10000 = 1%
        long wMax = LscUnitConstants.W_MAX_DEFAULT_PPM; // 20000 = 2%
        long rMin = LscUnitConstants.RELEASE_MIN_PPB;   // 500000 = 0.05%
        long rMax = LscUnitConstants.RELEASE_MAX_PPB;   // 1000000 = 0.10%

        // w=1% -> r=0.05%
        assertEquals(500000L, ReleaseRateCalculator.calcRatePpb(10000L, wMin, wMax, rMin, rMax));
        // w=1.5% -> r=0.075%
        assertEquals(750000L, ReleaseRateCalculator.calcRatePpb(15000L, wMin, wMax, rMin, rMax));
        // w=2% -> r=0.10%
        assertEquals(1000000L, ReleaseRateCalculator.calcRatePpb(20000L, wMin, wMax, rMin, rMax));
    }

    // ==================== T10: w分母为零或净消耗为负 -> 最低率 ====================
    @Test
    void t10_zeroDenominatorAndNegativeNet() {
        long wMin = LscUnitConstants.W_MIN_DEFAULT_PPM;
        long wMax = LscUnitConstants.W_MAX_DEFAULT_PPM;
        long rMin = LscUnitConstants.RELEASE_MIN_PPB;
        long rMax = LscUnitConstants.RELEASE_MAX_PPB;

        // w=0（分母为零）-> 最低率
        long rateZero = ReleaseRateCalculator.calcRatePpb(0L, wMin, wMax, rMin, rMax);
        assertEquals(500000L, rateZero, "T10: w=0时应使用最低释放率");

        // w负数（净消耗为负按0处理）-> 最低率
        long rateNeg = ReleaseRateCalculator.calcRatePpb(-5000L, wMin, wMax, rMin, rMax);
        assertEquals(500000L, rateNeg, "T10: w为负时应使用最低释放率");
    }

    // ==================== T11: w_min >= w_max -> 配置非法 ====================
    @Test
    void t11_invalidWConfig() {
        // w_min == w_max
        assertFalse(ReleaseRateCalculator.isValidWConfig(10000L, 10000L), "w_min不能等于w_max");
        // w_min > w_max
        assertFalse(ReleaseRateCalculator.isValidWConfig(20000L, 10000L), "w_min不能大于w_max");
        // 超出硬边界
        assertFalse(ReleaseRateCalculator.isValidWConfig(4000L, 20000L), "w_min低于硬下限5000");
        assertFalse(ReleaseRateCalculator.isValidWConfig(10000L, 26000L), "w_max高于硬上限25000");
        // 合法
        assertTrue(ReleaseRateCalculator.isValidWConfig(10000L, 20000L));
        assertTrue(ReleaseRateCalculator.isValidWConfig(5000L, 25000L));
    }

    // ==================== T12: 原始1 unit最低率运行2000日 -> 第2000日清零 ====================
    @Test
    void t12_oneUnitMinRate2000Days() {
        long original = 1L;
        long rate = LscUnitConstants.RELEASE_MIN_PPB; // 500000 ppb = 0.05%
        long remainder = 0L;
        long remaining = 1L;

        long totalReleased = 0;
        for (int day = 1; day <= 2000; day++) {
            long[] result = ReleaseRateCalculator.calcDailyRelease(original, rate, remainder, remaining);
            totalReleased += result[0];
            remainder = result[1];
            remaining -= result[0];
            if (day < 2000) {
                assertEquals(0L, result[0], "第" + day + "日不应释放（前1999日quota=0）");
            }
        }
        assertEquals(1L, totalReleased, "T12: 第2000日应释放1 unit，累计清零");
        assertEquals(0L, remaining);
    }

    // ==================== T13: 原始160000 unit最低率 -> 每日80 unit ====================
    @Test
    void t13_160000UnitMinRateDaily80() {
        long original = 160000L;
        long rate = LscUnitConstants.RELEASE_MIN_PPB; // 500000 ppb
        long remainder = 0L;
        long remaining = 160000L;

        long[] result = ReleaseRateCalculator.calcDailyRelease(original, rate, remainder, remaining);
        assertEquals(80L, result[0], "T13: 原始160000 unit最低率每日额度应为80 unit");
        // 160000 * 500000 / 1000000000 = 80
        assertEquals(0L, result[1], "余数应为0");
    }

    // ==================== 额外验证：释放率硬边界常量 ====================
    @Test
    void releaseRateHardBounds() {
        assertEquals(500000L, LscUnitConstants.RELEASE_MIN_PPB, "释放率下限0.05%");
        assertEquals(1000000L, LscUnitConstants.RELEASE_MAX_PPB, "释放率上限0.10%");
    }

    @Test
    void unitConversion() {
        assertEquals(10000L, LscUnitConstants.UNIT_PER_LSC, "1 LSC = 10000 unit");
        assertEquals(100L, LscUnitConstants.UNIT_PER_CENT, "100 unit = 1分");
        assertEquals(2600L, LscMathUtil.centToUnit(26), "26分 = 2600 unit");
    }

    // ==================== 额外：混合支付赠送不超过全额基准 ====================
    @Test
    void hybridGrantNeverExceedsFullRmbGrant() {
        long sale = 13000L;
        long cost = 8000L;
        long k = 800000L;
        long fullGrant = GrantCalculator.calcFullRmbGrant(sale, cost, k);

        // 不同人民币分摊比例，赠送不应超过全额基准
        for (long rmb = 100; rmb <= sale; rmb += 100) {
            long hybrid = GrantCalculator.calcHybridGrant(sale, cost, rmb, k);
            assertTrue(hybrid <= fullGrant,
                    "人民币分摊" + rmb + "分时赠送" + hybrid + "不应超过全额基准" + fullGrant);
        }
    }

    // ==================== 额外：单位转换与显示 ====================
    @Test
    void formatLscDisplay() {
        assertEquals("40.0000", LscMathUtil.formatLsc(400000L));
        assertEquals("33.8461", LscMathUtil.formatLsc(338461L));
        assertEquals("0.0000", LscMathUtil.formatLsc(0L));
        assertEquals("12.8000", LscMathUtil.formatLsc(128000L));
    }

    @Test
    void formatYuanDisplay() {
        assertEquals("130.00", LscMathUtil.formatYuan(13000L));
        assertEquals("0.01", LscMathUtil.formatYuan(1L));
    }
}
