package com.lianshengtong.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 奖励计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否首单 */
    private Boolean firstOrder;

    /** 推荐人用户ID(无推荐人则null) */
    private Long referrerId;

    /** 首单消费金额(元) */
    private BigDecimal firstOrderAmount;

    /** 奖励金额(元) = 首单金额 * 10% */
    private BigDecimal rewardAmount;

    /** 是否划转成功 */
    private Boolean success;

    /** 挂账ID(划转失败挂账时返回) */
    private Long pendingId;
}
