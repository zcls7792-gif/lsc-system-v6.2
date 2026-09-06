package com.lianshengtong.lsc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.entity.Orders;
import com.lianshengtong.lsc.security.UserContext;
import com.lianshengtong.lsc.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Long productId = Long.valueOf(items.get(0).get("productId").toString());
        Integer quantity = items.get(0).get("quantity") != null ? Integer.valueOf(items.get(0).get("quantity").toString()) : 1;
        Long lscAmount = body.get("lscAmount") != null ? Long.valueOf(body.get("lscAmount").toString()) : 0L;
        Long addressId = body.get("addressId") != null ? Long.valueOf(body.get("addressId").toString()) : null;
        return R.ok(orderService.createOrder(UserContext.getUserId(), productId, quantity, lscAmount, addressId));
    }

    @PostMapping("/offline")
    public R<Map<String, Object>> payOffline(@RequestBody Map<String, Object> body) {
        Long merchantId = Long.valueOf(body.get("merchantId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        Long lscAmount = body.get("lscAmount") != null ? Long.valueOf(body.get("lscAmount").toString()) : 0L;
        return R.ok(orderService.payOffline(UserContext.getUserId(), merchantId, amount, lscAmount));
    }

    @GetMapping
    public R<Page<Orders>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(orderService.list(UserContext.getUserId(), status, pageNo, pageSize));
    }

    @GetMapping("/{orderNo}")
    public R<Orders> detail(@PathVariable String orderNo) {
        return R.ok(orderService.detail(orderNo));
    }
}
