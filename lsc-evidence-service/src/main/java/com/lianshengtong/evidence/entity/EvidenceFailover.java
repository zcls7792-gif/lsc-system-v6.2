package com.lianshengtong.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 存证故障记录实体 (evidence_failover 表)
 * <p>上链失败超过3次重试后，记录至故障表，由定时任务(30分钟扫描)补传。</p>
 */
@TableName("evidence_failover")
public class EvidenceFailover implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联区块链存证记录ID */
    private Long blockchainRecordId;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 数据哈希 */
    private String dataHash;

    /** 失败原因 */
    private String failReason;

    /** 重试次数 */
    private Integer retryCount;

    /** 状态 0待补传 1已补传 2已废弃 */
    private Integer status;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public EvidenceFailover() {
    }

    public EvidenceFailover(Long id, Long blockchainRecordId, String bizType, String bizId, String dataHash, String failReason, Integer retryCount, Integer status, LocalDateTime nextRetryAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.blockchainRecordId = blockchainRecordId;
        this.bizType = bizType;
        this.bizId = bizId;
        this.dataHash = dataHash;
        this.failReason = failReason;
        this.retryCount = retryCount;
        this.status = status;
        this.nextRetryAt = nextRetryAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBlockchainRecordId() { return blockchainRecordId; }
    public void setBlockchainRecordId(Long blockchainRecordId) { this.blockchainRecordId = blockchainRecordId; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private EvidenceFailover obj = new EvidenceFailover();
        public Builder id(Long v) { obj.id = v; return this; }
        public Builder blockchainRecordId(Long v) { obj.blockchainRecordId = v; return this; }
        public Builder bizType(String v) { obj.bizType = v; return this; }
        public Builder bizId(String v) { obj.bizId = v; return this; }
        public Builder dataHash(String v) { obj.dataHash = v; return this; }
        public Builder failReason(String v) { obj.failReason = v; return this; }
        public Builder retryCount(Integer v) { obj.retryCount = v; return this; }
        public Builder status(Integer v) { obj.status = v; return this; }
        public Builder nextRetryAt(LocalDateTime v) { obj.nextRetryAt = v; return this; }
        public Builder createdAt(LocalDateTime v) { obj.createdAt = v; return this; }
        public Builder updatedAt(LocalDateTime v) { obj.updatedAt = v; return this; }
        public EvidenceFailover build() { return obj; }
    }

}
