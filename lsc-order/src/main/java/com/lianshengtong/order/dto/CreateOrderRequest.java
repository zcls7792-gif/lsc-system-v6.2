package com.lianshengtong.order.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    private Long userId;
    private String buyerType;
    private Long sellerEntityId;
    private List<SkuItem> skuItems;
    private Long shippingCent;
    private Long lscUnit;
    private Long couponId;
    private Long couponCent;

    @Data
    public static class SkuItem {
        private Long skuId;
        private Integer qty;
    }
}
