package com.lianshengtong.promotion.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 奖励回滚请求(首单全额退款时调用)
 */
@Data
public class RollbackRewardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被推荐人(消费者)用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 首单订单号 */
    @NotNull(message = "订单号不能为空")
    private String orderNo;
}
