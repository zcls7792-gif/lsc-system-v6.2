package com.lianshengtong.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 区块链存证记录实体 (blockchain_records 表)
 * <p>分库分表：lsc_evidence 库，按 biz_id 取模 32。</p>
 * <ul>
 *   <li>status: 0待上链 1已上链 2失败</li>
 * </ul>
 */
@TableName("blockchain_records")
public class BlockchainRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务类型(LEDGER/RECONCILE/PROMOTION/RISK等) */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 数据哈希(SHA-256) */
    private String dataHash;

    /** 原始数据(JSON摘要) */
    private String dataPayload;

    /** 状态 0待上链 1已上链 2失败 */
    private Integer status;

    /** 链上交易哈希 */
    private String chainTxHash;

    /** 区块高度 */
    private Long blockNumber;

    /** 重试次数 */
    private Integer retryCount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 完成时间(上链完成) */
    private LocalDateTime completedAt;

    public BlockchainRecord() {
    }

    public BlockchainRecord(Long id, String bizType, String bizId, String dataHash, String dataPayload, Integer status, String chainTxHash, Long blockNumber, Integer retryCount, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.bizType = bizType;
        this.bizId = bizId;
        this.dataHash = dataHash;
        this.dataPayload = dataPayload;
        this.status = status;
        this.chainTxHash = chainTxHash;
        this.blockNumber = blockNumber;
        this.retryCount = retryCount;
        this.remark = remark;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
    public String getDataPayload() { return dataPayload; }
    public void setDataPayload(String dataPayload) { this.dataPayload = dataPayload; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getChainTxHash() { return chainTxHash; }
    public void setChainTxHash(String chainTxHash) { this.chainTxHash = chainTxHash; }
    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private BlockchainRecord obj = new BlockchainRecord();
        public Builder id(Long v) { obj.id = v; return this; }
        public Builder bizType(String v) { obj.bizType = v; return this; }
        public Builder bizId(String v) { obj.bizId = v; return this; }
        public Builder dataHash(String v) { obj.dataHash = v; return this; }
        public Builder dataPayload(String v) { obj.dataPayload = v; return this; }
        public Builder status(Integer v) { obj.status = v; return this; }
        public Builder chainTxHash(String v) { obj.chainTxHash = v; return this; }
        public Builder blockNumber(Long v) { obj.blockNumber = v; return this; }
        public Builder retryCount(Integer v) { obj.retryCount = v; return this; }
        public Builder remark(String v) { obj.remark = v; return this; }
        public Builder createdAt(LocalDateTime v) { obj.createdAt = v; return this; }
        public Builder updatedAt(LocalDateTime v) { obj.updatedAt = v; return this; }
        public Builder completedAt(LocalDateTime v) { obj.completedAt = v; return this; }
        public BlockchainRecord build() { return obj; }
    }

}
