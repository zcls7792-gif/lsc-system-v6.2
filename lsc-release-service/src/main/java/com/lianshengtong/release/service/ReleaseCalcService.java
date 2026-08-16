package com.lianshengtong.release.service;

import com.lianshengtong.release.entity.DailyReleaseSummary;

import java.math.BigDecimal;

/**
 * 释放核心算法服务
 * <p>
 * 基于核销率反馈的消费权益凭证动态释放调节方法(核心专利算法)。
 * 严格约束：
 * <ul>
 *   <li>LSC数值使用bigint(Long)</li>
 *   <li>中间运算使用decimal(18,6)</li>
 *   <li>结果统一向下取整</li>
 *   <li>释放比例 rate 二次校验必须在 [0.03%, 0.05%] 硬约束内，越界终止任务并推送告警</li>
 * </ul>
 * </p>
 */
public interface ReleaseCalcService {

    /**
     * 计算核销率 k = N_total / M_total
     * <p>前一日全网核销总额 / 监管账户余额总和，结果保留6位小数。</p>
     *
     * @param nTotal 前一日全网核销总额
     * @param mTotal 监管账户余额总和
     * @return 核销率 k (decimal 18,6)
     */
    BigDecimal calcK(BigDecimal nTotal, BigDecimal mTotal);

    /**
     * 计算释放速率 rate
     * <ul>
     *   <li>k &lt;= 0.50% -&gt; rate = 0.05% (硬上限)</li>
     *   <li>k &gt;= 1.0%  -&gt; rate = 0.03% (硬下限)</li>
     *   <li>0.50% &lt; k &lt; 1.0% -&gt; rate = 0.075% - 0.05 * k</li>
     * </ul>
     *
     * @param k 核销率
     * @return 释放速率 rate (decimal 18,6)
     */
    BigDecimal calcRate(BigDecimal k);

    /**
     * 计算当日释放总量 T_release = rate * L_locked (向下取整)
     *
     * @param rate    释放速率
     * @param lLocked 全网锁定LSC总量
     * @return 当日释放总量(整数个LSC，向下取整)
     */
    long calcReleaseTotal(BigDecimal rate, long lLocked);

    /**
     * 二次校验释放比例 rate 是否在 [0.03%, 0.05%] 硬约束内。
     * <p>越界时终止任务推送并返回 false。</p>
     *
     * @param rate 待校验速率
     * @return true 合法；false 越界(已推送告警)
     */
    boolean validateRate(BigDecimal rate);

    /**
     * 执行一次完整的释放计算链路：k -> rate -> 校验 -> T_release，
     * 并将关键输入输出写入每日释放汇总。
     *
     * @param summary 待填充的每日释放汇总(M_total/N_total/L_locked 必填)
     * @return 填充后的汇总(含 k/rate/tRelease/status)
     */
    DailyReleaseSummary calcDailyRelease(DailyReleaseSummary summary);
}
