package com.lianshengtong.lsc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.entity.Merchant;
import com.lianshengtong.lsc.entity.NhLevel;
import com.lianshengtong.lsc.entity.Product;
import com.lianshengtong.lsc.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(adminService.getDashboard());
    }

    @GetMapping("/merchants")
    public R<Page<Merchant>> merchants(
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(adminService.getMerchants(auditStatus, pageNo, pageSize));
    }

    @PutMapping("/merchants/{id}/audit")
    public R<Void> auditMerchant(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminService.auditMerchant(id, Integer.valueOf(body.get("auditStatus").toString()),
                (String) body.get("rejectReason"));
        return R.ok();
    }

    @GetMapping("/products")
    public R<Page<Product>> products(
            @RequestParam(required = false) Integer aiReviewResult,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(adminService.getProducts(aiReviewResult, pageNo, pageSize));
    }

    @PutMapping("/products/{id}/review")
    public R<Void> reviewProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminService.reviewProduct(id, Integer.valueOf(body.get("aiReviewResult").toString()),
                (String) body.get("rejectReason"));
        return R.ok();
    }

    @GetMapping("/nh/levels")
    public R<List<NhLevel>> nhLevels() {
        return R.ok(adminService.getNhLevels());
    }
}
