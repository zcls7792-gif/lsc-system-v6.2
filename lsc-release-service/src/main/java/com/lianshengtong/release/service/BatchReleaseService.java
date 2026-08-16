package com.lianshengtong.release.service;

import com.lianshengtong.release.entity.DailyReleaseSummary;

/**
 * 批量释放服务
 * <p>
 * 驱动全网锁定转可用(L_LOCKED -> L_AVAILABLE)的批量原子操作。
 * 特性：
 * <ul>
 *   <li>每批默认10万条记录</li>
 *   <li>每批次校验总量一致性(本批释放量与计划一致)</li>
 *   <li>失败批次回滚并标记，不影响已完成批次</li>
 *   <li>全量完成后汇总校验(实际已释放 == 计划释放总量)</li>
 *   <li>断点续跑：基于汇总记录的 actualReleased 与批次进度恢复</li>
 * </ul>
 * </p>
 */
public interface BatchReleaseService {

    /**
     * 执行批量释放(支持断点续跑)。
     * <p>由分布式锁保障单实例运行，跨服务一致性由 Seata AT 保障。</p>
     *
     * @param summary 每日释放汇总(含 k/rate/tRelease，L_locked 必填)
     * @return 更新后的汇总(含 batchCount/failedBatchCount/actualReleased/status)
     */
    DailyReleaseSummary executeBatchRelease(DailyReleaseSummary summary);

    /**
     * 全量账务比对：实际已释放量 == 计划释放总量。
     *
     * @param summary 每日释放汇总
     * @return true 一致；false 不一致(需人工介入)
     */
    boolean reconcile(DailyReleaseSummary summary);
}
