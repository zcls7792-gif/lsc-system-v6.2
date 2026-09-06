package com.lianshengtong.lsc.controller;

import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.security.UserContext;
import com.lianshengtong.lsc.service.NhService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/nh")
@RequiredArgsConstructor
public class NhController {

    private final NhService nhService;

    @GetMapping("/quota")
    public R<Map<String, Object>> getQuota() {
        return R.ok(nhService.getQuota(UserContext.getUserId()));
    }

    @PostMapping("/apply")
    public R<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long lscAmount = Long.valueOf(body.get("lscAmount").toString());
        return R.ok(nhService.apply(UserContext.getUserId(), lscAmount));
    }

    @GetMapping("/records")
    public R<Object> records(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(nhService.getRecords(UserContext.getUserId(), pageNo, pageSize));
    }
}
