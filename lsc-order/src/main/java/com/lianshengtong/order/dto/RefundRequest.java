package com.lianshengtong.order.dto;

import lombok.Data;

@Data
public class RefundRequest {
    private Long orderId;
    private Long rmbCent;
    private String reason;
}
