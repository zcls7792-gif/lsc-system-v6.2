package com.lianshengtong.promotion.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推广奖励挂账实体 (promotion_pending 表)
 * <p>
 * 当首单奖励因账本服务不可用等原因无法即时划转时，写入挂账表，由每日定时任务扫描补发。
 * 分库分表：lsc_promotion 库，promotion_pending 表按 referrer_id 取模 32。
 * </p>
 * <ul>
 *   <li>status: 0待补发 1已补发 2已废弃(首单全额退款)</li>
 *   <li>retry_count: 补发重试次数</li>
 * </ul>
 */
@Data
@TableName("promotion_pending")
public class PromotionPending implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 被推荐人(消费者)用户ID */
    private Long userId;

    /** 推荐人用户ID(单一外键约束,严格一级) */
    private Long referrerId;

    /** 首单订单号 */
    private String orderNo;

    /** 首单消费金额(元) */
    private BigDecimal firstOrderAmount;

    /** 奖励金额(元) = 首单消费金额 * 10% */
    private BigDecimal rewardAmount;

    /** 状态 0待补发 1已补发 2已废弃 */
    private Integer status;

    /** 补发重试次数 */
    private Integer retryCount;

    /** 补发备注/失败原因 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
