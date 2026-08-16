package com.lianshengtong.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.order.dto.OrderCreateDTO;
import com.lianshengtong.order.dto.OrderPayDTO;
import com.lianshengtong.order.dto.OrderRefundDTO;
import com.lianshengtong.order.entity.Order;
import com.lianshengtong.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 订单接口
 */
@Tag(name = "订单", description = "线上商城/线下消费订单管理")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单(线上/线下，混合支付)")
    @PostMapping("/create")
    public R<Order> create(@Valid @RequestBody OrderCreateDTO dto) {
        return R.ok(orderService.createOrder(dto));
    }

    @Operation(summary = "商城下单(Feign 简化参数，由 lsc-mall-service 调用)")
    @PostMapping("/create-mall")
    public R<String> createMall(@RequestParam("productId") Long productId,
                                @RequestParam("merchantId") Long merchantId,
                                @RequestParam("consumerId") Long consumerId,
                                @RequestParam(value = "lscAmount", required = false, defaultValue = "0") Long lscAmount,
                                @RequestParam(value = "rmbAmount", required = false) BigDecimal rmbAmount,
                                @RequestParam("totalPrice") BigDecimal totalPrice) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(consumerId);
        dto.setMerchantId(merchantId);
        dto.setProductId(productId);
        dto.setQuantity(1);
        dto.setTotalPrice(totalPrice);
        dto.setLscAmount(lscAmount == null ? 0L : lscAmount);
        Order order = orderService.createOrder(dto);
        return R.ok(order.getOrderNo());
    }

    @Operation(summary = "支付订单")
    @PostMapping("/pay")
    public R<Order> pay(@Valid @RequestBody OrderPayDTO dto) {
        return R.ok(orderService.payOrder(dto));
    }

    @Operation(summary = "完成订单")
    @PostMapping("/complete")
    public R<Order> complete(@RequestParam String orderNo, @RequestParam Long operatorId) {
        return R.ok(orderService.completeOrder(orderNo, operatorId));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel")
    public R<Order> cancel(@RequestParam String orderNo, @RequestParam Long operatorId) {
        return R.ok(orderService.cancelOrder(orderNo, operatorId));
    }

    @Operation(summary = "退款(全额/部分，携带退款金额时走部分退款)")
    @PostMapping("/refund")
    public R<Order> refund(@Valid @RequestBody OrderRefundDTO dto) {
        boolean partial = (dto.getRefundLscAmount() != null && dto.getRefundLscAmount() > 0)
                || (dto.getRefundRmbAmount() != null && dto.getRefundRmbAmount().signum() > 0);
        return R.ok(partial ? orderService.partialRefund(dto) : orderService.refundOrder(dto));
    }

    @Operation(summary = "根据订单号查询订单详情")
    @GetMapping("/{orderNo}")
    public R<Order> detail(@PathVariable String orderNo) {
        return R.ok(orderService.getByOrderNo(orderNo));
    }

    @Operation(summary = "分页查询订单列表(兼容 page/size 与 pageNum/pageSize)")
    @GetMapping("/list")
    public R<IPage<Order>> list(@RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size,
                                @RequestParam(required = false) Integer pageNum,
                                @RequestParam(required = false) Integer pageSize,
                                @RequestParam(required = false) Long userId,
                                @RequestParam(required = false) Integer status,
                                @RequestParam(required = false) String orderNo,
                                @RequestParam(required = false) Integer orderType,
                                @RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate) {
        Integer p = page != null ? page : pageNum;
        Integer s = size != null ? size : pageSize;
        return R.ok(orderService.listOrders(p, s, userId, status, orderNo, orderType, startDate, endDate));
    }

    @Operation(summary = "导出订单列表(管理后台, CSV 流)")
    @GetMapping("/export")
    public R<java.util.List<java.util.Map<String, Object>>> export(@RequestParam(required = false) Long userId,
                                                                    @RequestParam(required = false) Integer status,
                                                                    @RequestParam(required = false) String orderNo,
                                                                    @RequestParam(required = false) Integer orderType,
                                                                    @RequestParam(required = false) String startDate,
                                                                    @RequestParam(required = false) String endDate) {
        // 简化实现：复用 listOrders 拉取大批量数据(单次最多1万条)返回前端, 由前端转为 Excel/CSV
        IPage<Order> page = orderService.listOrders(1, 10000, userId, status, orderNo, orderType, startDate, endDate);
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        if (page.getRecords() != null) {
            for (Order o : page.getRecords()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("orderNo", o.getOrderNo());
                row.put("orderType", o.getOrderType());
                row.put("consumerId", o.getConsumerId());
                row.put("merchantId", o.getMerchantId());
                row.put("productName", o.getProductName());
                row.put("quantity", o.getQuantity());
                row.put("totalPrice", o.getTotalPrice());
                row.put("lscAmount", o.getLscAmount());
                row.put("rmbAmount", o.getRmbAmount());
                row.put("status", o.getStatus());
                row.put("payTime", o.getPayTime());
                row.put("createdAt", o.getCreatedAt());
                rows.add(row);
            }
        }
        return R.ok(rows);
    }

    @Operation(summary = "按日期汇总订单支付金额(对账支付侧)")
    @GetMapping("/daily-summary")
    public R<java.util.Map<String, Object>> dailySummary(@RequestParam("date") String date) {
        java.time.LocalDate target = java.time.LocalDate.parse(date);
        return R.ok(orderService.dailySummary(target));
    }

    @Operation(summary = "商家发货/确认履约")
    @PostMapping("/ship")
    public R<Order> ship(@RequestBody java.util.Map<String, Object> body) {
        String orderNo = String.valueOf(body.get("orderNo"));
        Long operatorId = body.get("operatorId") == null ? null
                : Long.parseLong(String.valueOf(body.get("operatorId")));
        // 发货即标记订单为已完成(简化流程)
        return R.ok(orderService.completeOrder(orderNo, operatorId));
    }

    @Operation(summary = "商家同意退款")
    @PostMapping("/refund/agree")
    public R<Void> agreeRefund(@RequestBody java.util.Map<String, Object> body) {
        String orderNo = String.valueOf(body.get("orderNo"));
        Long operatorId = body.get("operatorId") == null ? null
                : Long.parseLong(String.valueOf(body.get("operatorId")));
        // 同意退款:执行全额退款流程
        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(orderNo);
        dto.setOperatorId(operatorId);
        orderService.refundOrder(dto);
        return R.ok();
    }

    @Operation(summary = "商家拒绝退款")
    @PostMapping("/refund/reject")
    public R<Void> rejectRefund(@RequestBody java.util.Map<String, Object> body) {
        // 拒绝退款:将订单状态从退款中恢复为已完成
        String orderNo = String.valueOf(body.get("orderNo"));
        Long operatorId = body.get("operatorId") == null ? null
                : Long.parseLong(String.valueOf(body.get("operatorId")));
        orderService.rejectRefund(orderNo, operatorId,
                body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        return R.ok();
    }

    @Operation(summary = "退款申请列表(商家端)")
    @GetMapping("/refund/list")
    public R<IPage<Order>> refundList(@RequestParam(required = false, defaultValue = "1") Integer page,
                                       @RequestParam(required = false, defaultValue = "20") Integer size,
                                       @RequestParam(required = false) Long merchantId,
                                       @RequestParam(required = false) Integer status) {
        // 简化实现:复用 listOrders 查询含退款状态的订单(4已退款/5部分退款)
        return R.ok(orderService.listOrders(page, size, merchantId, status, null, null, null, null));
    }

    @Operation(summary = "商家今日订单/收入统计")
    @GetMapping("/stats-today")
    public R<java.util.Map<String, Object>> statsToday(@RequestParam(required = false) Long merchantId) {
        return R.ok(orderService.statsToday(merchantId));
    }
}
