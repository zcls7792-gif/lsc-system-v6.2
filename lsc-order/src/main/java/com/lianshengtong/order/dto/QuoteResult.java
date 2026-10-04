package com.lianshengtong.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QuoteResult {
    private String quoteId;
    private Long goodsCent;
    private Long shippingCent;
    private Long maxDeductionUnit;
    private Long maxDeductionCent;
    private List<QuoteItemResult> items;
    private LocalDateTime expiresAt;

    @Data
    @Builder
    public static class QuoteItemResult {
        private Long skuId;
        private Integer qty;
        private Long unitPriceCent;
        private Long lineGoodsCent;
        private Long grantCoefficientPpm;
        private Long costCent;
        private Long grantUnitPerPiece;
    }
}
