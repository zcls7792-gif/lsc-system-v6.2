package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * LSC 账户实体 (lsc_accounts 表)
 * <p>
 * 以 user_id 为主键，记录每个用户的锁定余额与可用余额。
 * version 字段使用 {@link Version} 实现乐观锁，由 MyBatis-Plus 的
 * {@code OptimisticLockerInnerInterceptor} 自动维护。
 * </p>
 */
@TableName("lsc_accounts")
public class LscAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID(雪花算法)，主键 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 锁定LSC总量 */
    private Long totalLocked;

    /** 可用LSC总量 */
    private Long totalAvailable;

    /** 乐观锁版本号，更新失败抛出异常触发重试 */
    @Version
    private Integer version;

    /** 更新时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;



    public LscAccount() {
    }
    public LscAccount(Long userId, Long totalLocked, Long totalAvailable, Integer version, LocalDateTime updatedAt) {
        this.userId = userId;
        this.totalLocked = totalLocked;
        this.totalAvailable = totalAvailable;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTotalLocked() { return totalLocked; }
    public void setTotalLocked(Long totalLocked) { this.totalLocked = totalLocked; }
    public Long getTotalAvailable() { return totalAvailable; }
    public void setTotalAvailable(Long totalAvailable) { this.totalAvailable = totalAvailable; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private LscAccount obj = new LscAccount();
        public Builder userId(Long v) { obj.userId = v; return this; }
        public Builder totalLocked(Long v) { obj.totalLocked = v; return this; }
        public Builder totalAvailable(Long v) { obj.totalAvailable = v; return this; }
        public Builder version(Integer v) { obj.version = v; return this; }
        public Builder updatedAt(LocalDateTime v) { obj.updatedAt = v; return this; }
        public LscAccount build() { return obj; }
    }

}
