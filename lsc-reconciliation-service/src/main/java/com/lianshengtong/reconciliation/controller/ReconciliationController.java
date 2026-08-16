package com.lianshengtong.reconciliation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.result.R;
import com.lianshengtong.reconciliation.entity.ReconcileReport;
import com.lianshengtong.reconciliation.mapper.ReconcileReportMapper;
import com.lianshengtong.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 对账服务接口
 * <p>
 * 与 lsc-admin-web 的 reconcile.ts 契约对齐：
 * <ul>
 *   <li>GET /report 分页列表查询</li>
 *   <li>GET /report/{date} 单日详情</li>
 *   <li>POST /daily 触发对账(query param)</li>
 *   <li>POST /trigger 触发对账(JSON body, 兼容前端)</li>
 * </ul>
 * </p>
 */
@Tag(name = "对账服务", description = "每日对账/差异报告/哈希上链存证")
@RestController
@RequestMapping("/api/reconcile")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconcileReportMapper reconcileReportMapper;

    @Operation(summary = "触发每日对账(query param 形式)")
    @PostMapping("/daily")
    public R<ReconcileReport> dailyReconcile(@RequestParam(value = "date", required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(reconciliationService.dailyReconcile(date));
    }

    @Operation(summary = "触发每日对账(JSON body 形式, 兼容前端 trigger)")
    @PostMapping("/trigger")
    public R<ReconcileReport> trigger(@RequestBody(required = false) Map<String, String> body) {
        LocalDate date = null;
        if (body != null && body.get("date") != null && !body.get("date").isBlank()) {
            date = LocalDate.parse(body.get("date"));
        }
        return R.ok(reconciliationService.dailyReconcile(date));
    }

    @Operation(summary = "对账报告分页列表(管理后台)")
    @GetMapping("/report")
    public R<IPage<ReconcileReport>> reportList(@RequestParam(required = false, defaultValue = "1") Integer page,
                                                 @RequestParam(required = false, defaultValue = "20") Integer size,
                                                 @RequestParam(required = false) String date,
                                                 @RequestParam(required = false) Integer status) {
        Page<ReconcileReport> p = new Page<>(page, size);
        LambdaQueryWrapper<ReconcileReport> wrapper = new LambdaQueryWrapper<>();
        if (date != null && !date.isBlank()) {
            wrapper.eq(ReconcileReport::getReconcileDate, LocalDate.parse(date));
        }
        if (status != null) {
            wrapper.eq(ReconcileReport::getStatus, status);
        }
        wrapper.orderByDesc(ReconcileReport::getReconcileDate);
        return R.ok(reconcileReportMapper.selectPage(p, wrapper));
    }

    @Operation(summary = "查询对账报告(单日详情)")
    @GetMapping("/report/{date}")
    public R<ReconcileReport> report(@PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(reconciliationService.generateReport(date));
    }
}
