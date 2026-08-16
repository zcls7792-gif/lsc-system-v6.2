package com.lianshengtong.b2b.entity;

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
 * B2B 交易订单实体 (b2b_orders 表)
 * <p>
 * 记录商家间 LSC 1:1 流转订单。idempotent_key 上有唯一索引防重，
 * version 字段使用 {@link Version} 实现乐观锁，由 MyBatis-Plus
 * {@code OptimisticLockerInnerInterceptor} 自动维护。
 * ai_verification_result / ai_verification_score 由 AI 网关回写。
 * </p>
 */
@Data
@TableName("b2b_orders")
public class B2bOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** B2B订单号 */
    private String orderNo;

    /** 发起方商家ID */
    private Long initiatorId;

    /** 接收方商家ID */
    private Long counterpartyId;

    /** 交易描述 */
    private String tradeDescription;

    /** 交易总金额(元) */
    private BigDecimal totalAmountRmb;

    /** LSC流转数量(1:1对应总金额) */
    private Long lscAmount;

    /** 合同编号 */
    private String contractNo;

    /** 贸易凭证图片(JSON数组) */
    private String tradeEvidenceUrls;

    /** AI核验结果 0未核验 1AI真实 2AI可疑 3人工真实 4人工虚假 */
    private Integer aiVerificationResult;

    /** AI核验评分0-100 */
    private BigDecimal aiVerificationScore;

    /** AI风险标签(JSON) */
    private String aiRiskTags;

    /** 对手方是否确认 0否 1是 */
    private Integer counterpartyConfirmed;

    /** 确认人 */
    private String confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** LSC是否已流转 0否 1是 */
    private Integer lscTransferred;

    /** 订单过期时间(默认创建后7天) */
    private LocalDateTime expireAt;

    /** 订单状态 0待确认 1已确认 2已流转 3已完成 4已取消 5已作废 */
    private Integer status;

    /** 幂等校验唯一键(唯一索引) */
    private String idempotentKey;

    /** 乐观锁版本号，更新失败抛出异常触发重试 */
    @Version
    private Integer version;

    /** 创建时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 更新时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
