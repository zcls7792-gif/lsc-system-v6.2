package com.lianshengtong.ledger.dto;

import lombok.Data;

/**
 * 支付占用命令
 */
@Data
public class ReserveCommand {
    private Long userId;
    private Long orderId;
    private Long reserveUnit;         // 占用 unit（必须为100整数倍）
    private String businessKey;
    private java.time.LocalDateTime expiresAt;
}
