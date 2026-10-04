package com.lianshengtong.order.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuoteRequest {
    private String buyerType;  // C/B
    private List<SkuItem> skuItems;
    private Long shippingCent;
    private Long deductionPpm;

    @Data
    public static class SkuItem {
        private Long skuId;
        private Integer qty;
    }
}
