package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * LSC 流水实体 (lsc_transactions 表)
 * <p>
 * 每笔账务操作落一条流水，idempotent_key 上有唯一索引防重。
 * 同时记录操作前后的锁定/可用余额快照，便于审计与对账。
 * </p>
 */
@TableName("lsc_transactions")
public class LscTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 流水类型 1消费发行 2每日释放 3推广奖励 4商城消费 5线下消费 6过期转回 7商家核销 8B2B流转 9退款退回 */
    private Integer type;

    /** 数量 */
    private Long amount;

    /** 操作前锁定余额 */
    private Long beforeLocked;

    /** 操作后锁定余额 */
    private Long afterLocked;

    /** 操作前可用余额 */
    private Long beforeAvailable;

    /** 操作后可用余额 */
    private Long afterAvailable;

    /** 交易对手方用户ID */
    private Long counterpartyId;

    /** 关联订单号 */
    private String orderNo;

    /** 幂等校验唯一键(唯一索引) */
    private String idempotentKey;

    /** 备注 */
    private String remark;

    /** 创建时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;



    public LscTransaction() {
    }
    public LscTransaction(Long id, Long userId, Integer type, Long amount, Long beforeLocked, Long afterLocked, Long beforeAvailable, Long afterAvailable, Long counterpartyId, String orderNo, String idempotentKey, String remark, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.beforeLocked = beforeLocked;
        this.afterLocked = afterLocked;
        this.beforeAvailable = beforeAvailable;
        this.afterAvailable = afterAvailable;
        this.counterpartyId = counterpartyId;
        this.orderNo = orderNo;
        this.idempotentKey = idempotentKey;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Long getBeforeLocked() { return beforeLocked; }
    public void setBeforeLocked(Long beforeLocked) { this.beforeLocked = beforeLocked; }
    public Long getAfterLocked() { return afterLocked; }
    public void setAfterLocked(Long afterLocked) { this.afterLocked = afterLocked; }
    public Long getBeforeAvailable() { return beforeAvailable; }
    public void setBeforeAvailable(Long beforeAvailable) { this.beforeAvailable = beforeAvailable; }
    public Long getAfterAvailable() { return afterAvailable; }
    public void setAfterAvailable(Long afterAvailable) { this.afterAvailable = afterAvailable; }
    public Long getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(Long counterpartyId) { this.counterpartyId = counterpartyId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private LscTransaction obj = new LscTransaction();
        public Builder id(Long v) { obj.id = v; return this; }
        public Builder userId(Long v) { obj.userId = v; return this; }
        public Builder type(Integer v) { obj.type = v; return this; }
        public Builder amount(Long v) { obj.amount = v; return this; }
        public Builder beforeLocked(Long v) { obj.beforeLocked = v; return this; }
        public Builder afterLocked(Long v) { obj.afterLocked = v; return this; }
        public Builder beforeAvailable(Long v) { obj.beforeAvailable = v; return this; }
        public Builder afterAvailable(Long v) { obj.afterAvailable = v; return this; }
        public Builder counterpartyId(Long v) { obj.counterpartyId = v; return this; }
        public Builder orderNo(String v) { obj.orderNo = v; return this; }
        public Builder idempotentKey(String v) { obj.idempotentKey = v; return this; }
        public Builder remark(String v) { obj.remark = v; return this; }
        public Builder createdAt(LocalDateTime v) { obj.createdAt = v; return this; }
        public LscTransaction build() { return obj; }
    }

}
