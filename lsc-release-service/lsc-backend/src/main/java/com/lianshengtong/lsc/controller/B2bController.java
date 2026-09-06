package com.lianshengtong.lsc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.entity.B2bOrder;
import com.lianshengtong.lsc.security.UserContext;
import com.lianshengtong.lsc.service.B2bService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/b2b")
@RequiredArgsConstructor
public class B2bController {

    private final B2bService b2bService;

    @PostMapping("/orders")
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return R.ok(b2bService.createOrder(
                UserContext.getUserId(),
                Long.valueOf(body.get("counterpartyId").toString()),
                (String) body.get("tradeDescription"),
                new BigDecimal(body.get("totalAmountRmb").toString()),
                Long.valueOf(body.get("lscAmount").toString()),
                (String) body.get("contractNo"),
                (String) body.get("tradeEvidenceUrls")));
    }

    @PutMapping("/orders/{orderNo}/confirm")
    public R<Map<String, Object>> confirm(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        boolean confirmed = Boolean.TRUE.equals(body.get("confirmed"));
        return R.ok(b2bService.confirm(orderNo, confirmed, UserContext.getUserId()));
    }

    @GetMapping("/orders")
    public R<Page<B2bOrder>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(b2bService.list(UserContext.getUserId(), status, pageNo, pageSize));
    }
}
