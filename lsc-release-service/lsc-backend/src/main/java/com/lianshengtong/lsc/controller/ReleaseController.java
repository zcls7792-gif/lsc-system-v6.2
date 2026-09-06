package com.lianshengtong.lsc.controller;

import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.service.ReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/release")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseService releaseService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(required = false) String date) {
        return R.ok(releaseService.getSummary(date));
    }

    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        return R.ok(releaseService.getConfig());
    }

    @PutMapping("/config")
    public R<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        return R.ok(releaseService.updateConfig(
                new BigDecimal(body.get("kMin").toString().replace("%", "")),
                new BigDecimal(body.get("kMax").toString().replace("%", "")),
                new BigDecimal(body.get("alpha").toString())));
    }
}
