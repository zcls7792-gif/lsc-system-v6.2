package com.lianshengtong.reconciliation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.utils.EvidenceHashUtil;
import com.lianshengtong.reconciliation.entity.ReconcileReport;
import com.lianshengtong.reconciliation.feign.EvidenceFeignClient;
import com.lianshengtong.reconciliation.feign.LscLedgerFeignClient;
import com.lianshengtong.reconciliation.feign.OrderFeignClient;
import com.lianshengtong.reconciliation.mapper.ReconcileReportMapper;
import com.lianshengtong.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 对账服务实现
 * <p>
 * 每日凌晨比对支付机构流水与 LSC 账本流水，差异超阈值生成告警，
 * 结果 SHA-256 哈希通过 Feign 调用存证服务上链。Redisson 分布式锁防止重复执行。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconcileReportMapper reconcileReportMapper;
    private final EvidenceFeignClient evidenceFeignClient;
    private final LscLedgerFeignClient lscLedgerFeignClient;
    private final OrderFeignClient orderFeignClient;
    private final RedissonClient redissonClient;

    @Override
    @Scheduled(cron = "${lsc.reconcile.daily-cron:0 0 3 * * ?}")
    @Transactional(rollbackFor = Exception.class)
    public ReconcileReport dailyReconcile(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now().minusDays(1) : date;
        String lockKey = "lsc:reconcile:lock:" + targetDate;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(5, 30, TimeUnit.MINUTES)) {
                throw new BizException("对账任务正在执行，请勿重复触发 date=" + targetDate);
            }
            log.info("开始每日对账 date={}", targetDate);
            return generateReport(targetDate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("对账任务被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconcileReport generateReport(LocalDate date) {
        if (date == null) {
            throw new BizException("对账日期不能为空");
        }
        // 查询是否已生成
        LambdaQueryWrapper<ReconcileReport> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ReconcileReport::getReconcileDate, date);
        ReconcileReport exist = reconcileReportMapper.selectOne(existWrapper);
        if (exist != null && exist.getStatus() != null && exist.getStatus() != 0) {
            return exist;
        }
        ReconcileReport report = exist != null ? exist : new ReconcileReport();
        report.setReconcileDate(date);
        report.setStatus(0);
        if (exist == null) {
            reconcileReportMapper.insert(report);
        }

        // 实际从支付机构对账文件/接口 + lsc-ledger-service 拉取当日流水
        // 支付侧基准：以订单已支付总额(order-service 聚合)；真实部署应替换为支付机构对账文件
        Map<String, Object> paymentSummary = fetchPaymentSummary(date);
        BigDecimal paymentTotal = toBigDecimal(paymentSummary.get("totalAmount"));
        Long paymentCount = toLong(paymentSummary.get("totalCount"));
        // 账本侧：通过 Feign 调 ledger-service 按类型聚合
        // 类型4=商城消费 5=线下消费 9=退款退回
        Map<String, Object> ledgerSummary = fetchLedgerSummary(date);
        BigDecimal ledgerTotal = toBigDecimal(ledgerSummary.get("totalAmount"));
        Long ledgerCount = toLong(ledgerSummary.get("totalCount"));

        report.setPaymentTotalAmount(paymentTotal);
        report.setPaymentCount(paymentCount);
        report.setLedgerTotalAmount(ledgerTotal);
        report.setLedgerCount(ledgerCount);

        BigDecimal diffAmount = paymentTotal.subtract(ledgerTotal).abs();
        long diffCount = Math.abs(paymentCount - ledgerCount);
        report.setDiffAmount(diffAmount);
        report.setDiffCount(diffCount);

        Map<String, Object> diffDetail = new HashMap<>();
        diffDetail.put("paymentTotal", paymentTotal);
        diffDetail.put("ledgerTotal", ledgerTotal);
        diffDetail.put("diffAmount", diffAmount);
        diffDetail.put("diffCount", diffCount);
        report.setDiffDetail(com.alibaba.fastjson2.JSON.toJSONString(diffDetail));

        // 一致: 差异金额 < 阈值且差异笔数为0
        boolean consistent = diffAmount.compareTo(new BigDecimal("0.01")) < 0 && diffCount == 0;
        report.setStatus(consistent ? 1 : 2);

        // 计算结果哈希
        String resultHash = EvidenceHashUtil.sha256Hex(report);
        report.setResultHash(resultHash);

        reconcileReportMapper.updateById(report);
        log.info("对账报告生成完成 date={} status={} diffAmount={}", date, report.getStatus(), diffAmount);

        // 哈希上链
        if (consistent) {
            try {
                hashOnChain(report.getId());
            } catch (RuntimeException e) {
                log.error("对账结果上链失败 reportId={}", report.getId(), e);
            }
        }
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String hashOnChain(Long reportId) {
        ReconcileReport report = reconcileReportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(404, "对账报告不存在");
        }
        if (report.getResultHash() == null) {
            report.setResultHash(EvidenceHashUtil.sha256Hex(report));
        }
        R<String> resp = evidenceFeignClient.saveEvidence("RECONCILE",
                String.valueOf(reportId), report.getResultHash());
        String chainTxHash = (resp != null && resp.isSuccess()) ? resp.getData() : null;
        report.setChainTxHash(chainTxHash);
        reconcileReportMapper.updateById(report);
        log.info("对账结果上链完成 reportId={} chainTxHash={}", reportId, chainTxHash);
        return chainTxHash;
    }

    /**
     * 拉取支付机构流水汇总(单次 Feign 调用)
     * <p>当前以订单已支付总额作为支付侧基准(由 order-service 聚合)；
     * 真实部署应替换为对接支付机构对账文件/接口的拉取逻辑。</p>
     */
    private Map<String, Object> fetchPaymentSummary(LocalDate date) {
        try {
            R<Map<String, Object>> resp = orderFeignClient.dailySummary(date.toString());
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData();
            }
        } catch (RuntimeException e) {
            log.warn("拉取支付侧汇总失败 date={} err={}", date, e.getMessage());
        }
        Map<String, Object> empty = new HashMap<>();
        empty.put("totalAmount", BigDecimal.ZERO);
        empty.put("totalCount", 0L);
        return empty;
    }

    /**
     * 拉取 LSC 账本流水汇总(单次 Feign 调 ledger-service 聚合)
     * <p>对账范围：商城消费(4) + 线下消费(5) + 退款退回(9)，单位为 LSC 数量(1:1对应人民币元)。</p>
     */
    private Map<String, Object> fetchLedgerSummary(LocalDate date) {
        try {
            // 4=商城消费 5=线下消费 9=退款退回
            R<Map<String, Object>> resp = lscLedgerFeignClient.dailySummary(date, "4,5,9");
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData();
            }
        } catch (RuntimeException e) {
            log.warn("拉取账本侧汇总失败 date={} err={}", date, e.getMessage());
        }
        Map<String, Object> empty = new HashMap<>();
        empty.put("totalAmount", BigDecimal.ZERO);
        empty.put("totalCount", 0L);
        return empty;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) {
            return BigDecimal.ZERO;
        }
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
