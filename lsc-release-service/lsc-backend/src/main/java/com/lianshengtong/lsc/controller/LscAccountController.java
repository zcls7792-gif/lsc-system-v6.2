package com.lianshengtong.lsc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.entity.LscTransaction;
import com.lianshengtong.lsc.security.UserContext;
import com.lianshengtong.lsc.service.LscAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/lsc")
@RequiredArgsConstructor
public class LscAccountController {

    private final LscAccountService lscAccountService;

    @GetMapping("/account")
    public R<Map<String, Object>> getAccount() {
        return R.ok(lscAccountService.getAccount(UserContext.getUserId()));
    }

    @GetMapping("/transactions")
    public R<Page<LscTransaction>> getTransactions(
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(lscAccountService.getTransactions(UserContext.getUserId(), type, pageNo, pageSize));
    }
}
