package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 可用 LSC 明细实体 (available_lsc_details 表)
 * <p>
 * 按批次记录每一笔可用LSC的数量、来源、过期日期与状态。
 * 过期转回扫描 status=1 且 expire_date &lt; 今天 的记录。
 * </p>
 */
@TableName("available_lsc_details")
public class AvailableLscDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 数量 */
    private Long amount;

    /** 来源类型(对应 LscTransactionTypeEnum.desc) */
    private String sourceType;

    /** 溯源ID(关联流水ID或订单号) */
    private Long sourceId;

    /** 原始过期日期 */
    private LocalDate originalExpireDate;

    /** 过期日期(B2B流转接收方会重置365天) */
    private LocalDate expireDate;

    /** 状态 1有效 2过期转回 3已使用 4已核销 5退款退回 */
    private Integer status;

    /** 创建时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;



    public AvailableLscDetail() {
    }
    public AvailableLscDetail(Long id, Long userId, Long amount, String sourceType, Long sourceId, LocalDate originalExpireDate, LocalDate expireDate, Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.originalExpireDate = originalExpireDate;
        this.expireDate = expireDate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public LocalDate getOriginalExpireDate() { return originalExpireDate; }
    public void setOriginalExpireDate(LocalDate originalExpireDate) { this.originalExpireDate = originalExpireDate; }
    public LocalDate getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private AvailableLscDetail obj = new AvailableLscDetail();
        public Builder id(Long v) { obj.id = v; return this; }
        public Builder userId(Long v) { obj.userId = v; return this; }
        public Builder amount(Long v) { obj.amount = v; return this; }
        public Builder sourceType(String v) { obj.sourceType = v; return this; }
        public Builder sourceId(Long v) { obj.sourceId = v; return this; }
        public Builder originalExpireDate(LocalDate v) { obj.originalExpireDate = v; return this; }
        public Builder expireDate(LocalDate v) { obj.expireDate = v; return this; }
        public Builder status(Integer v) { obj.status = v; return this; }
        public Builder createdAt(LocalDateTime v) { obj.createdAt = v; return this; }
        public Builder updatedAt(LocalDateTime v) { obj.updatedAt = v; return this; }
        public AvailableLscDetail build() { return obj; }
    }

}
