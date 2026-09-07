package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Order;
import com.lianshengtong.api.repository.OrderRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private static final String[] STATUS_DESC = {"待支付", "已支付", "已完成", "已取消", "已退款", "部分退款"};

    private final OrderRepository orderRepo;

    public OrderController(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Order>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long merchantId) {
        List<Order> filtered = orderRepo.findAll().stream().filter(o -> {
            if (status != null && !status.equals(o.getStatus())) return false;
            if (merchantId != null && !merchantId.equals(o.getMerchantId())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> detail(@PathVariable String orderNo) {
        return orderRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("订单不存在"));
    }

    @GetMapping("/detail")
    public ApiResponse<Order> detailByParam(@RequestParam String orderNo) {
        return detail(orderNo);
    }

    @GetMapping("/stats-today")
    public ApiResponse<Map<String, Object>> statsToday(@RequestParam(required = false) Long merchantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("orderCount", 38);
        stats.put("totalAmount", 12856.50);
        stats.put("lscAmount", 6428);
        stats.put("rmbAmount", 6428.50);
        stats.put("refundCount", 2);
        stats.put("refundAmount", 199.00);
        return ApiResponse.success(stats);
    }

    /** 创建订单落库 */
    @PostMapping("/create")
    public ApiResponse<Order> create(@RequestBody Order body) {
        long newId = (orderRepo.count() + 1);
        body.setId(newId);
        if (body.getOrderNo() == null || body.getOrderNo().isEmpty()) {
            body.setOrderNo("LS" + System.currentTimeMillis());
        }
        if (body.getStatus() == null) body.setStatus(0);
        if (body.getStatusDesc() == null && body.getStatus() != null && body.getStatus() >= 0 && body.getStatus() < STATUS_DESC.length) {
            body.setStatusDesc(STATUS_DESC[body.getStatus()]);
        }
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return ApiResponse.success(orderRepo.save(body));
    }

    /** 支付订单落库：status 0 -> 1 */
    @PostMapping("/pay")
    public ApiResponse<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        Map<String, Object> r = new LinkedHashMap<>();
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(1);
                        o.setStatusDesc("已支付");
                        orderRepo.save(o);
                    });
        }
        r.put("status", 1);
        r.put("statusDesc", "已支付");
        return ApiResponse.success(r);
    }

    /** 取消订单落库：status -> 3 */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(3);
                        o.setStatusDesc("已取消");
                        orderRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    /** 确认收货落库：status -> 2 */
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(2);
                        o.setStatusDesc("已完成");
                        orderRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>(body);
        r.put("totalAmount", 99.9);
        r.put("lscAmount", 49);
        r.put("rmbAmount", 50.9);
        return ApiResponse.success(r);
    }

    @GetMapping("/refund/list")
    public ApiResponse<PageResult<Order>> refundList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Order> refunds = orderRepo.findAll().stream()
                .filter(o -> o.getStatus() != null && (o.getStatus() == 4 || o.getStatus() == 5))
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(refunds, page, size));
    }

    /** 申请退款落库：status -> 4 */
    @PostMapping("/refund/apply")
    public ApiResponse<Void> refundApply(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(4);
                        o.setStatusDesc("已退款");
                        orderRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> export() {
        return ApiResponse.success(new LinkedHashMap<>(Map.of("url", "/files/orders.xlsx")));
    }
}
