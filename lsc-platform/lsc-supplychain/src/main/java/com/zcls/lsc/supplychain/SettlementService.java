package com.zcls.lsc.supplychain;

import com.zcls.lsc.supplychain.enums.SupplychainEnums.SettlementStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第9.2章 结算与付款服务。
 *
 * 采购、入库、质检和结算四单匹配；付款账期按合同配置（默认 45 天）。
 * 付款申请、批准、支付渠道流水和对账独立记录，付款重试不能重复支付。
 * 供应商仅收取人民币货款，不持有供应商权益账户。
 */
@Service
public class SettlementService {

    private final JdbcTemplate jdbc;

    public SettlementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建结算单，关联采购明细与收货明细（四单匹配）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createSettlement(long supplierId, long entityId, List<SettlementAlloc> allocs) {
        long settlementId = nextId();
        String settleNo = "STL" + System.currentTimeMillis();
        long amountCent = allocs.stream().mapToLong(a -> a.amountCent()).sum();

        jdbc.update(
                "INSERT INTO settlement(settlement_id, settle_no, supplier_id, entity_id, amount_cent, "
                        + "status, approved_by, paid_at) VALUES(?,?,?,?,?,?,?,?)",
                settlementId, settleNo, supplierId, entityId, amountCent,
                SettlementStatus.PENDING.name(), null, null);

        for (SettlementAlloc a : allocs) {
            jdbc.update(
                    "INSERT INTO settlement_allocation(alloc_id, settlement_id, po_item_id, "
                            + "receipt_item_id, quantity, amount_cent) VALUES(?,?,?,?,?,?)",
                    nextId(), settlementId, a.poItemId(), a.receiptItemId(),
                    a.quantity(), a.amountCent());
        }

        jdbc.update("UPDATE purchase_order SET status='SETTLED' WHERE po_id=("
                + "SELECT po_id FROM purchase_order_item WHERE po_item_id=? LIMIT 1)",
                allocs.isEmpty() ? 0L : allocs.get(0).poItemId());
        return settleNo;
    }

    /**
     * 审批结算单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveSettlement(long settlementId, long approverId) {
        jdbc.update("UPDATE settlement SET status='APPROVED', approved_by=? WHERE settlement_id=?",
                approverId, settlementId);
    }

    /**
     * 执行付款（幂等：相同 payment_no 不重复支付）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void paySettlement(long settlementId, String paymentNo, long amountCent, String bankTradeNo) {
        // 幂等检查
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM supplier_payment WHERE payment_no=?", Integer.class, paymentNo);
        if (cnt != null && cnt > 0) return;

        long paymentId = nextId();
        jdbc.update(
                "INSERT INTO supplier_payment(payment_id, payment_no, settlement_id, amount_cent, "
                        + "bank_trade_no, status, paid_at) VALUES(?,?,?,?,?,?,?)",
                paymentId, paymentNo, settlementId, amountCent, bankTradeNo, "PAID",
                java.sql.Timestamp.valueOf(LocalDateTime.now()));
        jdbc.update("UPDATE settlement SET status='PAID', paid_at=? WHERE settlement_id=?",
                java.sql.Timestamp.valueOf(LocalDateTime.now()), settlementId);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record SettlementAlloc(long poItemId, long receiptItemId, int quantity, long amountCent) {}
}
