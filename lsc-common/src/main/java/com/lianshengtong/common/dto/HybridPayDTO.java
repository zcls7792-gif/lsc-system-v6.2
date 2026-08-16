package com.lianshengtong.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class HybridPayDTO implements Serializable {

    private Long lscAmount;
    private BigDecimal rmbAmount;
    private BigDecimal totalPrice;

    public HybridPayDTO() {}

    public HybridPayDTO(Long lscAmount, BigDecimal rmbAmount, BigDecimal totalPrice) {
        this.lscAmount = lscAmount;
        this.rmbAmount = rmbAmount;
        this.totalPrice = totalPrice;
    }

    
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private HybridPayDTO obj = new HybridPayDTO();
        public Builder lscAmount(Long v) { obj.lscAmount = v; return this; }
        public Builder rmbAmount(BigDecimal v) { obj.rmbAmount = v; return this; }
        public Builder totalPrice(BigDecimal v) { obj.totalPrice = v; return this; }
        public HybridPayDTO build() { return obj; }
    }


    public Long getLscAmount() { return lscAmount; }
    public void setLscAmount(Long v) { this.lscAmount = v; }
    public BigDecimal getRmbAmount() { return rmbAmount; }
    public void setRmbAmount(BigDecimal v) { this.rmbAmount = v; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal v) { this.totalPrice = v; }


}
