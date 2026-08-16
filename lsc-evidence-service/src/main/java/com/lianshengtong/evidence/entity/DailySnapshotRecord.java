package com.lianshengtong.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 每日快照存证记录 (daily_snapshot_records 表)
 * <p>每日将当日所有区块链存证记录的哈希构建 Merkle 树，根哈希上链。</p>
 */
@TableName("daily_snapshot_records")
public class DailySnapshotRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 当日存证记录数 */
    private Long recordCount;

    /** Merkle 树根哈希 */
    private String merkleRoot;

    /** 链上交易哈希 */
    private String chainTxHash;

    /** 状态 0待上链 1已上链 2失败 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public DailySnapshotRecord() {
    }

    public DailySnapshotRecord(Long id, LocalDate snapshotDate, Long recordCount, String merkleRoot, String chainTxHash, Integer status, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.snapshotDate = snapshotDate;
        this.recordCount = recordCount;
        this.merkleRoot = merkleRoot;
        this.chainTxHash = chainTxHash;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }
    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }
    public String getChainTxHash() { return chainTxHash; }
    public void setChainTxHash(String chainTxHash) { this.chainTxHash = chainTxHash; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private DailySnapshotRecord obj = new DailySnapshotRecord();
        public Builder id(Long v) { obj.id = v; return this; }
        public Builder snapshotDate(LocalDate v) { obj.snapshotDate = v; return this; }
        public Builder recordCount(Long v) { obj.recordCount = v; return this; }
        public Builder merkleRoot(String v) { obj.merkleRoot = v; return this; }
        public Builder chainTxHash(String v) { obj.chainTxHash = v; return this; }
        public Builder status(Integer v) { obj.status = v; return this; }
        public Builder remark(String v) { obj.remark = v; return this; }
        public Builder createdAt(LocalDateTime v) { obj.createdAt = v; return this; }
        public Builder updatedAt(LocalDateTime v) { obj.updatedAt = v; return this; }
        public DailySnapshotRecord build() { return obj; }
    }

}
