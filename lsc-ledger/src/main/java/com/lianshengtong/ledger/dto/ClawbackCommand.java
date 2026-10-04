package com.lianshengtong.ledger.dto;

import lombok.Data;

/**
 * 赠送撤回命令（V7.7.2 第七章 7.5）
 */
@Data
public class ClawbackCommand {
    private Long userId;
    private Long refundId;
    private Long orderId;             // 源订单
    private Long sourceItemId;        // 源订单行
    private Long grantLotId;          // 源GrantLot
    private Long clawbackUnit;        // 应撤回 unit
    private String businessKey;
}
