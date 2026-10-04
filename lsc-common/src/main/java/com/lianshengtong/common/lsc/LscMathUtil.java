package com.lianshengtong.common.lsc;

import java.math.BigInteger;

/**
 * LSC 整型计算工具（V7.7.2 第四章 4.1）
 * <p>
 * 所有账务计算使用整数 ppm/ppb，禁止 double/float 参与账务。
 * 中间乘法使用 {@link BigInteger} 防止溢出，结果向下取整（floor）。
 * </p>
 */
public final class LscMathUtil {

    private LscMathUtil() {}

    /**
     * 计算 floor(value * ppm / 1000000)
     * 用于：赠送计算、抵扣上限计算等。
     *
     * @param value 基数（unit 或 cent）
     * @param ppm   百万分比
     * @return floor(value * ppm / 1_000_000)
     */
    public static long mulPpmFloor(long value, long ppm) {
        if (value == 0 || ppm == 0) return 0L;
        BigInteger v = BigInteger.valueOf(value);
        BigInteger p = BigInteger.valueOf(ppm);
        BigInteger base = BigInteger.valueOf(LscUnitConstants.PPM_BASE);
        return v.multiply(p).divide(base).longValueExact();
    }

    /**
     * 计算 floor(value * ppb / 1000000000)
     * 用于：每日释放额度计算。
     *
     * @param value 基数（original_grant_unit）
     * @param ppb   十亿分比（释放率）
     * @return floor(value * ppb / 1_000_000_000)
     */
    public static long mulPpbFloor(long value, long ppb) {
        if (value == 0 || ppb == 0) return 0L;
        BigInteger v = BigInteger.valueOf(value);
        BigInteger p = BigInteger.valueOf(ppb);
        BigInteger base = BigInteger.valueOf(LscUnitConstants.PPB_BASE);
        return v.multiply(p).divide(base).longValueExact();
    }

    /**
     * 计算 floor(a * b / c)，c > 0。
     * 使用 BigInteger 防止中间溢出。
     */
    public static long mulDivFloor(long a, long b, long c) {
        if (c <= 0) throw new ArithmeticException("divisor must be positive");
        if (a == 0 || b == 0) return 0L;
        BigInteger ba = BigInteger.valueOf(a);
        BigInteger bb = BigInteger.valueOf(b);
        BigInteger bc = BigInteger.valueOf(c);
        return ba.multiply(bb).divide(bc).longValueExact();
    }

    /**
     * 带余数的除法：numerator / denominator，返回 [quotient, remainder]。
     * 用于释放余数累计。
     */
    public static long[] divMod(long numerator, long denominator) {
        if (denominator <= 0) throw new ArithmeticException("denominator must be positive");
        long q = numerator / denominator;
        long r = numerator % denominator;
        return new long[]{q, r};
    }

    /**
     * 将分转换为 unit：cent * 100。
     */
    public static long centToUnit(long cent) {
        return Math.multiplyExact(cent, LscUnitConstants.UNIT_PER_CENT);
    }

    /**
     * 将 unit 转换为分（向下取整）：floor(unit / 100)。
     */
    public static long unitToCentFloor(long unit) {
        return unit / LscUnitConstants.UNIT_PER_CENT;
    }

    /**
     * 校验 unit 是否为 100 的整数倍（抵扣必须）。
     */
    public static boolean isUnitMultipleOf100(long unit) {
        return unit >= 0 && unit % LscUnitConstants.UNIT_PER_CENT == 0;
    }

    /**
     * 将 unit 格式化为 LSC 显示字符串（4 位小数）。
     * 例：400000 -> "40.0000"，338461 -> "33.8461"。
     */
    public static String formatLsc(long unit) {
        long lsc = unit / LscUnitConstants.UNIT_PER_LSC;
        long remainder = unit % LscUnitConstants.UNIT_PER_LSC;
        return String.format("%d.%04d", lsc, remainder);
    }

    /**
     * 将分格式化为元字符串（2 位小数）。
     */
    public static String formatYuan(long cent) {
        long yuan = cent / LscUnitConstants.CENT_PER_YUAN;
        long remainder = cent % LscUnitConstants.CENT_PER_YUAN;
        return String.format("%d.%02d", yuan, remainder);
    }

    /**
     * 计算最大余数法分配剩余分数。
     * 将 totalRemainder 个 1 分分配给各单元，按余数降序、相同则按 id 升序。
     *
     * @param remainders 各单元的小数余数（已乘 1000000 放大为整数便于比较）
     * @param ids        各单元的排序 id
     * @param extra      待分配的剩余分数
     * @return 各单元应额外加的分数数组
     */
    public static long[] largestRemainderAllocate(long[] remainders, long[] ids, long extra) {
        int n = remainders.length;
        long[] result = new long[n];
        if (extra <= 0 || n == 0) return result;

        // 构建索引并按余数降序、id 升序排序
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> {
            int cmp = Long.compare(remainders[b], remainders[a]);
            if (cmp != 0) return cmp;
            return Long.compare(ids[a], ids[b]);
        });

        long allocated = 0;
        for (int i = 0; i < n && allocated < extra; i++) {
            result[idx[i]]++;
            allocated++;
        }
        return result;
    }
}
