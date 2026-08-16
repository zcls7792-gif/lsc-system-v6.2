package com.lianshengtong.common.dto;

import java.io.Serializable;

public class LscLedgerOpDTO implements Serializable {

    private String idempotentKey;
    private Integer transactionType;
    private Long userId;
    private Long counterpartyId;
    private Long lockedDelta;
    private Long availableDelta;
    private String orderNo;
    private String remark;

    public LscLedgerOpDTO() {}

    public LscLedgerOpDTO(String idempotentKey, Integer transactionType, Long userId,
            Long counterpartyId, Long lockedDelta, Long availableDelta,
            String orderNo, String remark) {
        this.idempotentKey = idempotentKey;
        this.transactionType = transactionType;
        this.userId = userId;
        this.counterpartyId = counterpartyId;
        this.lockedDelta = lockedDelta;
        this.availableDelta = availableDelta;
        this.orderNo = orderNo;
        this.remark = remark;
    }

    
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private LscLedgerOpDTO obj = new LscLedgerOpDTO();
        public Builder idempotentKey(String v) { obj.idempotentKey = v; return this; }
        public Builder transactionType(Integer v) { obj.transactionType = v; return this; }
        public Builder userId(Long v) { obj.userId = v; return this; }
        public Builder counterpartyId(Long v) { obj.counterpartyId = v; return this; }
        public Builder lockedDelta(Long v) { obj.lockedDelta = v; return this; }
        public Builder availableDelta(Long v) { obj.availableDelta = v; return this; }
        public Builder orderNo(String v) { obj.orderNo = v; return this; }
        public Builder remark(String v) { obj.remark = v; return this; }
        public LscLedgerOpDTO build() { return obj; }
    }


    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String v) { this.idempotentKey = v; }
    public Integer getTransactionType() { return transactionType; }
    public void setTransactionType(Integer v) { this.transactionType = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(Long v) { this.counterpartyId = v; }
    public Long getLockedDelta() { return lockedDelta; }
    public void setLockedDelta(Long v) { this.lockedDelta = v; }
    public Long getAvailableDelta() { return availableDelta; }
    public void setAvailableDelta(Long v) { this.availableDelta = v; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String v) { this.orderNo = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { this.remark = v; }


}
