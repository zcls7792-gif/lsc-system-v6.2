package com.zcls.lsc.supplychain;

import com.zcls.lsc.supplychain.enums.SupplychainEnums.SupplierStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第9.1章 供应商准入服务。
 *
 *  - 保存主体、许可、联系人、对公账户及合同
 *  - 银行账户变更必须由非原录入人复核；敏感账户脱敏显示、加密保存
 *  - 供应商既不能获得 LSC，也不以 LSC 抵货款
 */
@Service
public class SupplierService {

    private final JdbcTemplate jdbc;

    public SupplierService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 新增供应商。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createSupplier(String legalName, String licenseNo, String contactEnc,
                               String bankAccountEnc, int paymentTermDays) {
        long supplierId = nextId();
        jdbc.update(
                "INSERT INTO supplier(supplier_id, legal_entity_info, license_ref, contact_enc, "
                        + "bank_account_enc, bank_account_version, payment_term_days, status) "
                        + "VALUES(?,?,?,?,?,?,?,?)",
                supplierId, "{\"legalName\":\"" + legalName + "\"}", licenseNo,
                contactEnc, bankAccountEnc, 1, paymentTermDays, SupplierStatus.ACTIVE.name());
        return supplierId;
    }

    /**
     * 第9.1章 银行账户变更：必须由非原录入人复核。
     *
     * @param supplierId   供应商ID
     * @param newBankAccEnc 新加密银行账户
     * @param reviewerId   复核人ID（必须不等于录入人）
     * @param createdBy    原录入人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeBankAccount(long supplierId, String newBankAccEnc, long reviewerId, long createdBy) {
        if (reviewerId == createdBy) {
            throw new IllegalArgumentException("bank account change must be reviewed by a different person");
        }
        Integer version = jdbc.queryForObject(
                "SELECT bank_account_version FROM supplier WHERE supplier_id=?", Integer.class, supplierId);
        jdbc.update(
                "UPDATE supplier SET bank_account_enc=?, bank_account_version=? WHERE supplier_id=?",
                newBankAccEnc, version + 1, supplierId);
    }

    /**
     * 供应商状态变更（暂停/黑名单）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(long supplierId, SupplierStatus status) {
        jdbc.update("UPDATE supplier SET status=? WHERE supplier_id=?", status.name(), supplierId);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
