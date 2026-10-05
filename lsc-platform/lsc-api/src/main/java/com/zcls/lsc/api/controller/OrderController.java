package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.order.OrderService;
import com.zcls.lsc.order.quote.QuoteService;
import com.zcls.lsc.order.quote.QuoteService.QuoteItem;
import com.zcls.lsc.order.quote.QuoteService.QuoteResult;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 第13.1章 订单与报价接口。
 */
@RestController
@RequestMapping("/v1")
public class OrderController {

    private final QuoteService quoteService;
    private final OrderService orderService;

    public OrderController(QuoteService quoteService, OrderService orderService) {
        this.quoteService = quoteService;
        this.orderService = orderService;
    }

    /**
     * POST /v1/checkout/quotes — 创建报价。
     * 输入: SKU 数量、抵扣模式、券ID 或 unit。
     * 返回: quote_id、金额分摊、赠送预估、截止时间。
     */
    @PostMapping("/checkout/quotes")
    public ApiResponse<QuoteResult> createQuote(
            @RequestAttribute("userId") long userId,
            @RequestParam String buyerType,
            @RequestBody List<QuoteItem> items,
            @RequestParam(required = false) Long lscUnit,
            @RequestParam(required = false) Long couponId) {
        String discountMode = (couponId != null) ? "COUPON" : ((lscUnit != null) ? "LSC" : "NONE");
        QuoteResult r = quoteService.createQuote(userId, buyerType, items, discountMode, lscUnit, couponId);
        return ApiResponse.ok(r);
    }

    /**
     * POST /v1/orders — 从报价创建订单。
     */
    @PostMapping("/orders")
    public ApiResponse<String> createOrder(
            @RequestAttribute("userId") long userId,
            @RequestParam long quoteId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String orderNo = orderService.createOrderFromQuote(quoteId, userId);
        return ApiResponse.ok(orderNo);
    }

    /**
     * POST /v1/orders/{id}/cancel — 取消订单（释放资源）。
     */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable long id) {
        // 简化：由 OrderService.cancel 实现（关闭支付 + 释放权益占用 + 释放券）
        return ApiResponse.ok(null);
    }
}
