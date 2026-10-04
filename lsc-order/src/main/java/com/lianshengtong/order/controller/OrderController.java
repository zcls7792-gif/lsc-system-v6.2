package com.lianshengtong.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.result.R;
import com.lianshengtong.order.dto.*;
import com.lianshengtong.order.entity.Order;
import com.lianshengtong.order.entity.OrderItem;
import com.lianshengtong.order.mapper.OrderItemMapper;
import com.lianshengtong.order.mapper.OrderMapper;
import com.lianshengtong.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单接口（V7.7.2 第十三章 13.1）
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @PostMapping("/checkout/quotes")
    public R<QuoteResult> createQuote(@RequestBody QuoteRequest req) {
        return R.ok(orderService.createQuote(req));
    }

    @PostMapping("/orders")
    public R<Order> createOrder(@RequestBody CreateOrderRequest req,
                                 @RequestHeader("Idempotency-Key") String idemKey) {
        return R.ok(orderService.createOrder(req));
    }

    /** 订单列表 */
    @GetMapping("/orders")
    public R<IPage<Order>> listOrders(@RequestAttribute("userId") Long userId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String paymentStatus,
                                       @RequestParam(required = false) String fulfillmentStatus) {
        Page<Order> p = new Page<>(page, size);
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getOrderId);
        if (paymentStatus != null && !paymentStatus.isEmpty()) {
            w.eq(Order::getPaymentStatus, paymentStatus);
        }
        if (fulfillmentStatus != null && !fulfillmentStatus.isEmpty()) {
            w.eq(Order::getFulfillmentStatus, fulfillmentStatus);
        }
        return R.ok(orderMapper.selectPage(p, w));
    }

    /** 订单详情 */
    @GetMapping("/orders/{id}")
    public R<Map<String, Object>> getOrder(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) return R.fail(404, "订单不存在");
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        data.put("items", items);
        return R.ok(data);
    }

    @PostMapping("/orders/{id}/cancel")
    public R<Void> cancelOrder(@PathVariable Long id) {
        // 简化：取消订单释放占用
        return R.ok();
    }

    @PostMapping("/refunds")
    public R<Void> createRefund(@RequestBody RefundRequest req) {
        orderService.createRefund(req);
        return R.ok();
    }

    /** 支付成功回调（内部/测试） */
    @PostMapping("/internal/payments/{orderId}/success")
    public R<Void> onPaymentSuccess(@PathVariable Long orderId) {
        orderService.onPaymentSuccess(orderId);
        return R.ok();
    }

    /** 支付渠道回调入口 */
    @PostMapping("/internal/payments/callback/{channel}")
    public R<Map<String, Object>> paymentCallback(@PathVariable String channel,
                                                   @RequestBody Map<String, Object> payload) {
        // 简化：实际应校验渠道签名
        Object orderIdObj = payload.get("orderId");
        Object tradeNoObj = payload.get("channelTradeNo");
        Object amountObj = payload.get("amountCent");
        if (orderIdObj == null) {
            return R.fail(400, "缺少订单号");
        }
        Long orderId = Long.valueOf(orderIdObj.toString());
        orderService.onPaymentSuccess(orderId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("channel", channel);
        result.put("orderId", orderId);
        result.put("tradeNo", tradeNoObj);
        return R.ok(result);
    }

    /** 订单完成（发放赠送LSC） */
    @PostMapping("/orders/{orderId}/complete")
    public R<Void> completeOrder(@PathVariable Long orderId) {
        orderService.completeOrder(orderId);
        return R.ok();
    }
}
