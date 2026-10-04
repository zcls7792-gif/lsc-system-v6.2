package com.lianshengtong.ledger.dto;

import lombok.Data;

import java.util.List;

/**
 * 退款返还命令
 */
@Data
public class RefundRestoreCommand {
    private Long userId;
    private Long refundId;
    private Long orderId;
    /** 返还明细：原消费分配ID -> 返还unit */
    private List<RestoreItem> items;
    private String businessKey;

    @Data
    public static class RestoreItem {
        private Long consumptionId;       // 原消费分配
        private Long availableLotId;      // 原批次
        private Long restoreUnit;         // 返还 unit
        private boolean originalExpired;  // 原批次是否已过期
    }
}
