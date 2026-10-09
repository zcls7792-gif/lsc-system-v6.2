package com.zcls.lsc.account.ledger;

import com.zcls.lsc.account.enums.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账本写入命令（6.1）。
 * 正常写入在同一数据库事务内完成事件、分录、账户投影及 Outbox。
 */
public final class LedgerCommand {

    private final long userId;
    private final EventType eventType;
    private final String businessKey;
    private final String requestHash;
    private final Long orderId;
    private final Long refundId;
    private final Long caseId;
    private final LocalDate businessDate;
    private final LocalDateTime occurredAt;
    private final String ruleVersion;
    private final Long originalEventId;
    private final String payloadJson;
    private final List<BucketDelta> entries;

    private LedgerCommand(Builder b) {
        this.userId = b.userId;
        this.eventType = b.eventType;
        this.businessKey = b.businessKey;
        this.requestHash = b.requestHash;
        this.orderId = b.orderId;
        this.refundId = b.refundId;
        this.caseId = b.caseId;
        this.businessDate = b.businessDate;
        this.occurredAt = b.occurredAt;
        this.ruleVersion = b.ruleVersion;
        this.originalEventId = b.originalEventId;
        this.payloadJson = b.payloadJson;
        this.entries = b.entries;
    }

    public long userId() { return userId; }
    public EventType eventType() { return eventType; }
    public String businessKey() { return businessKey; }
    public String requestHash() { return requestHash; }
    public Long orderId() { return orderId; }
    public Long refundId() { return refundId; }
    public Long caseId() { return caseId; }
    public LocalDate businessDate() { return businessDate; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public String ruleVersion() { return ruleVersion; }
    public Long originalEventId() { return originalEventId; }
    public String payloadJson() { return payloadJson; }
    public List<BucketDelta> entries() { return entries; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private long userId;
        private EventType eventType;
        private String businessKey;
        private String requestHash;
        private Long orderId;
        private Long refundId;
        private Long caseId;
        private LocalDate businessDate;
        private LocalDateTime occurredAt;
        private String ruleVersion = "v7.7.2";
        private Long originalEventId;
        private String payloadJson;
        private List<BucketDelta> entries;

        public Builder userId(long v) { this.userId = v; return this; }
        public Builder eventType(EventType v) { this.eventType = v; return this; }
        public Builder businessKey(String v) { this.businessKey = v; return this; }
        public Builder requestHash(String v) { this.requestHash = v; return this; }
        public Builder orderId(Long v) { this.orderId = v; return this; }
        public Builder refundId(Long v) { this.refundId = v; return this; }
        public Builder caseId(Long v) { this.caseId = v; return this; }
        public Builder businessDate(LocalDate v) { this.businessDate = v; return this; }
        public Builder occurredAt(LocalDateTime v) { this.occurredAt = v; return this; }
        public Builder ruleVersion(String v) { this.ruleVersion = v; return this; }
        public Builder originalEventId(Long v) { this.originalEventId = v; return this; }
        public Builder payloadJson(String v) { this.payloadJson = v; return this; }
        public Builder entries(List<BucketDelta> v) { this.entries = v; return this; }

        public LedgerCommand build() {
            if (businessDate == null) throw new IllegalStateException("businessDate required");
            if (occurredAt == null) throw new IllegalStateException("occurredAt required");
            if (entries == null) throw new IllegalStateException("entries required");
            return new LedgerCommand(this);
        }
    }
}
