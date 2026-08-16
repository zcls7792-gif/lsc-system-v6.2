package com.lianshengtong.promotion.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 首单判定请求
 */
@Data
public class FirstOrderCheckDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被推荐人(消费者)用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 推荐人用户ID(严格一级, users.referrer_id; 无推荐人传null) */
    private Long referrerId;

    /** 订单号 */
    @NotNull(message = "订单号不能为空")
    private String orderNo;

    /** 订单消费金额(元) */
    @NotNull(message = "订单金额不能为空")
    private BigDecimal orderAmount;

    /** 订单状态: 2已完成(必填，需已完成且未全额退款) */
    @NotNull(message = "订单状态不能为空")
    private Integer orderStatus;

    /** 退款金额(元，无退款传0) */
    private BigDecimal refundAmount;
}
