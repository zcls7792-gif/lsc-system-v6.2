package com.lianshengtong.reconciliation.service;

import com.lianshengtong.reconciliation.entity.ReconcileReport;

import java.time.LocalDate;

/**
 * 对账服务接口
 * <p>每日比对支付机构流水与 LSC 账本流水、差异报告生成、结果哈希上链存证。</p>
 */
public interface ReconciliationService {

    /**
     * 每日对账
     * <p>凌晨比对支付机构流水与 LSC 账本流水，生成差异报告并哈希上链存证。</p>
     *
     * @param date 对账日期(空则取昨天)
     * @return 对账报告
     */
    ReconcileReport dailyReconcile(LocalDate date);

    /**
     * 生成差异报告
     *
     * @param date 对账日期
     * @return 对账报告
     */
    ReconcileReport generateReport(LocalDate date);

    /**
     * 结果哈希上链存证
     *
     * @param reportId 对账报告ID
     * @return 链上交易哈希
     */
    String hashOnChain(Long reportId);
}
