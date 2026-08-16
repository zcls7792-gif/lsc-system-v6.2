package com.lianshengtong.risk.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 风控日志实体 (risk_logs 表)
 * <p>
 * 记录所有风控检测结果。分库分表：lsc_risk 库，risk_logs 表按 user_id 取模 32。
 * </p>
 * <ul>
 *   <li>risk_type: 1批量下单 2异常混合支付 3高频套利 4异地操作 5AI动态</li>
 *   <li>risk_level: 1低 2中 3高</li>
 *   <li>handle_status: 0待处理 1已自动限制 2已推送人工审核 3已忽略 4已解封</li>
 * </ul>
 */
@Data
@TableName("risk_logs")
public class RiskLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 风控类型 1批量下单 2异常混合支付 3高频套利 4异地操作 5AI动态 */
    private Integer riskType;

    /** 风险等级 1低 2中 3高 */
    private Integer riskLevel;

    /** AI评分(0~100，AI动态风控时填充) */
    private Integer aiScore;

    /** 命中规则描述 */
    private String hitRule;

    /** 风险明细(JSON: 订单号、金额、IP、城市等) */
    private String detail;

    /** 处理状态 0待处理 1已自动限制 2已推送人工审核 3已忽略 4已解封 */
    private Integer handleStatus;

    /** 处理备注 */
    private String handleRemark;

    /** 操作IP */
    private String clientIp;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
