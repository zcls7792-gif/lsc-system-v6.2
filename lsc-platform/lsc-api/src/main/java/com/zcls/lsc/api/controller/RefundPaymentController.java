package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.coupon.CouponService;
import com.zcls.lsc.order.PaymentService;
import com.zcls.lsc.order.RefundOrderService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 第13.1章 退款与支付回调接口。
 */
@RestController
@RequestMapping("/v1")
public class RefundPaymentController {

    private final RefundOrderService refundOrderService;
    private final PaymentService paymentService;
    private final CouponService couponService;

    public RefundPaymentController(RefundOrderService refundOrderService, PaymentService paymentService,
                                   CouponService couponService) {
        this.refundOrderService = refundOrderService;
        this.paymentService = paymentService;
        this.couponService = couponService;
    }

    /**
     * POST /v1/refunds — 申请退款。
     * 输入: 单元数量、原因、退款类型。
     * 返回: refund_no、人民币与权益分别预估。
     */
    @PostMapping("/refunds")
    public ApiResponse<String> createRefund(
            @RequestAttribute("userId") long userId,
            @RequestParam long orderId,
            @RequestParam long itemId,
            @RequestParam String refundKind,
            @RequestParam(defaultValue = "0") int qty,
            @RequestParam long rmbCent,
            @RequestParam(required = false) String reason) {
        String refundNo = refundOrderService.createRefund(orderId, itemId, refundKind, qty, rmbCent, reason);
        return ApiResponse.ok(refundNo);
    }

    /**
     * POST /v1/refunds/{id}/succeed — 人民币退款成功后处理权益。
     * （实际由支付渠道回调或财务确认触发，此为内部接口）
     */
    @PostMapping("/internal/refunds/{id}/succeed")
    public ApiResponse<Void> refundSucceeded(@PathVariable long id) {
        refundOrderService.onRefundSucceeded(id);
        return ApiResponse.ok(null);
    }

    /**
     * POST /v1/internal/payments/callback/{channel} — 支付回调。
     * 内部接口，由支付渠道网关调用。
     */
    @PostMapping("/internal/payments/callback/{channel}")
    public ApiResponse<PaymentService.CallbackResult> paymentCallback(
            @PathVariable String channel,
            @RequestParam String merchantId,
            @RequestParam String channelTradeNo,
            @RequestParam String merchantRequestNo,
            @RequestParam long amountCent,
            @RequestParam String channelPaidAt,
            @RequestParam boolean signatureValid) {
        PaymentService.CallbackResult r = paymentService.handleCallback(
                channel, merchantId, channelTradeNo, merchantRequestNo, amountCent,
                LocalDateTime.parse(channelPaidAt), signatureValid);
        return ApiResponse.ok(r);
    }
}
