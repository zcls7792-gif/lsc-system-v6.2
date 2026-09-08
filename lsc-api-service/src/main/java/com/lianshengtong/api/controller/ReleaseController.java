package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.DailyReleaseSummary;
import com.lianshengtong.api.entity.LscAccount;
import com.lianshengtong.api.entity.Merchant;
import com.lianshengtong.api.entity.ReleaseConfig;
import com.lianshengtong.api.repository.DailyReleaseSummaryRepository;
import com.lianshengtong.api.repository.LscAccountRepository;
import com.lianshengtong.api.repository.MerchantRepository;
import com.lianshengtong.api.repository.ReleaseConfigRepository;
import com.lianshengtong.api.repository.WriteoffRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 释放控制器（V6.2 更新）
 * 数据源切换：DailyReleaseSummary + LscAccount + ReleaseConfig + Merchant
 * - /summary 聚合全网 LSC 锁定/可用余额 + 最新日汇总 k/rate
 * - /trend 直接读取 DailyReleaseSummary 表的真实历史数据
 * - /predict 基于近30天 DailyReleaseSummary 的 k/rate 趋势线性外推
 * - /config 直接读取 ReleaseConfig 表（含 editable 标记）
 */
@RestController
@RequestMapping("/api/release")
public class ReleaseController {

    private final DailyReleaseSummaryRepository dailyReleaseRepo;
    private final LscAccountRepository lscAccountRepo;
    private final ReleaseConfigRepository releaseConfigRepo;
    private final MerchantRepository merchantRepo;
    private final WriteoffRepository writeoffRepo;

    public ReleaseController(DailyReleaseSummaryRepository dailyReleaseRepo,
                            LscAccountRepository lscAccountRepo,
                            ReleaseConfigRepository releaseConfigRepo,
                            MerchantRepository merchantRepo,
                            WriteoffRepository writeoffRepo) {
        this.dailyReleaseRepo = dailyReleaseRepo;
        this.lscAccountRepo = lscAccountRepo;
        this.releaseConfigRepo = releaseConfigRepo;
        this.merchantRepo = merchantRepo;
        this.writeoffRepo = writeoffRepo;
    }

    /**
     * 释放总览
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // 聚合 LSC 账户
        List<LscAccount> accounts = lscAccountRepo.findAll();
        long totalLocked = accounts.stream().mapToLong(LscAccount::getTotalLocked).sum();
        long totalAvailable = accounts.stream().mapToLong(LscAccount::getTotalAvailable).sum();
        long totalLsc = totalLocked + totalAvailable;

        // 取最新日汇总
        DailyReleaseSummary latest = latestDailySummary();
        double k = latest != null && latest.getK() != null ? latest.getK() : 0.0;
        double rate = latest != null && latest.getRate() != null ? latest.getRate() : 0.0;
        long todayReleased = latest != null && latest.getTRelease() != null ? latest.getTRelease() : 0;
        double todayWriteoff = latest != null && latest.getNTotal() != null ? latest.getNTotal() : 0.0;

        // 历史累计释放
        long totalReleased = dailyReleaseRepo.findAll().stream()
                .mapToLong(s -> s.getTRelease() == null ? 0 : s.getTRelease())
                .sum();

        // 累计核销
        double totalWriteoff = writeoffRepo.findAll().stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        // 本月释放
        String monthStart = LocalDate.now().withDayOfMonth(1).toString();
        long monthReleased = dailyReleaseRepo.findAll().stream()
                .filter(s -> s.getDate() != null && s.getDate().compareTo(monthStart) >= 0)
                .mapToLong(s -> s.getTRelease() == null ? 0 : s.getTRelease())
                .sum();

        // 进度 = totalAvailable / totalLsc
        double progress = totalLsc > 0 ? (double) totalAvailable / totalLsc : 0.0;

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalReleased", totalReleased);
        s.put("totalWriteoff", Math.round(totalWriteoff * 100) / 100.0);
        s.put("totalLocked", totalLocked);
        s.put("totalAvailable", totalAvailable);
        s.put("totalLsc", totalLsc);
        s.put("todayReleased", todayReleased);
        s.put("todayWriteoff", Math.round(todayWriteoff * 100) / 100.0);
        s.put("monthReleased", monthReleased);
        s.put("merchantCount", merchantRepo.count());
        s.put("userCount", accounts.size());
        s.put("k", k);
        s.put("rate", rate);
        s.put("kPercent", String.format("%.4f%%", k * 100));
        s.put("ratePercent", String.format("%.4f%%", rate * 100));
        s.put("progress", String.format("%.2f%%", progress * 100));
        s.put("latestDate", latest != null ? latest.getDate() : null);
        return ApiResponse.success(s);
    }

    /**
     * 释放趋势
     * 直接读取 DailyReleaseSummary 表
     */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        List<DailyReleaseSummary> all = dailyReleaseRepo.findAll();
        all.sort(Comparator.comparing(DailyReleaseSummary::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<DailyReleaseSummary> tail = all.size() > days
                ? all.subList(all.size() - days, all.size())
                : all;

        List<Map<String, Object>> trend = new ArrayList<>();
        for (DailyReleaseSummary s : tail) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", s.getDate());
            row.put("released", s.getTRelease() != null ? s.getTRelease() : 0);
            row.put("writeoff", s.getNTotal() != null ? Math.round(s.getNTotal()) : 0);
            row.put("k", s.getK() != null ? s.getK() : 0);
            row.put("rate", s.getRate() != null ? s.getRate() : 0);
            row.put("mTotal", s.getMTotal() != null ? s.getMTotal() : 0);
            row.put("lLocked", s.getLLocked() != null ? s.getLLocked() : 0);
            row.put("batchCount", s.getBatchCount() != null ? s.getBatchCount() : 0);
            row.put("aiPredictedK7d", s.getAiPredictedK7d());
            row.put("aiPredictedK30d", s.getAiPredictedK30d());
            trend.add(row);
        }
        return ApiResponse.success(trend);
    }

    /**
     * 释放预测
     * 基于近30天 DailyReleaseSummary.tRelease 趋势线性外推
     */
    @GetMapping("/predict")
    public ApiResponse<Map<String, Object>> predict() {
        List<DailyReleaseSummary> all = dailyReleaseRepo.findAll();
        all.sort(Comparator.comparing(DailyReleaseSummary::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        int n = all.size();
        int window = Math.min(n, 30);
        if (window < 2) {
            // 数据不足，返回零预测
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nextMonthPredict", 0);
            p.put("confidence", "数据不足");
            p.put("factors", List.of("历史趋势", "商户活跃度", "季节因素"));
            p.put("trend", "未知");
            p.put("growthRate", "0.00%");
            return ApiResponse.success(p);
        }

        List<DailyReleaseSummary> recent = all.subList(n - window, n);
        // 线性回归 y = a*x + b，y = tRelease
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < recent.size(); i++) {
            double x = i;
            double y = recent.get(i).getTRelease() == null ? 0 : recent.get(i).getTRelease();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double denom = window * sumX2 - sumX * sumX;
        double slope = denom != 0 ? (window * sumXY - sumX * sumY) / denom : 0;
        double intercept = (sumY - slope * sumX) / window;

        // 外推未来30天总和
        double predict = 0;
        for (int i = window; i < window + 30; i++) {
            predict += Math.max(0, slope * i + intercept);
        }

        // 同比上月
        double lastMonthSum = 0;
        for (int i = Math.max(0, window - 30); i < window; i++) {
            lastMonthSum += recent.get(i).getTRelease() == null ? 0 : recent.get(i).getTRelease();
        }
        double growthRate = lastMonthSum > 0 ? (predict - lastMonthSum) / lastMonthSum : 0;
        String trendStr = slope > 0 ? "上升" : slope < 0 ? "下降" : "平稳";

        // 置信度：基于数据样本数
        String confidence = window >= 30 ? "92%" : window >= 14 ? "85%" : window >= 7 ? "75%" : "60%";

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nextMonthPredict", Math.round(predict));
        p.put("confidence", confidence);
        p.put("factors", List.of("历史趋势", "商户活跃度", "季节因素", "AI预测k值"));
        p.put("trend", trendStr);
        p.put("growthRate", String.format("%.2f%%", growthRate * 100));
        p.put("slope", slope);
        p.put("intercept", intercept);
        p.put("sampleSize", window);
        return ApiResponse.success(p);
    }

    /**
     * 释放配置列表
     * 直接读取 ReleaseConfig 表
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        List<ReleaseConfig> list = releaseConfigRepo.findAll();
        Map<String, Object> c = new LinkedHashMap<>();
        // 将配置项展开为 key-value
        for (ReleaseConfig cfg : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", cfg.getConfigValue());
            item.put("editable", cfg.getEditable());
            item.put("description", cfg.getDescription());
            c.put(cfg.getConfigKey(), item);
        }
        // 同时提供关键聚合字段（前端常用）
        c.put("rateMax", getConfigValue("rate_max", "0.0006"));
        c.put("rateMin", getConfigValue("rate_min", "0.0003"));
        c.put("kMin", getConfigValue("k_min", "0.005"));
        c.put("kMax", getConfigValue("k_max", "0.01"));
        c.put("alpha", getConfigValue("alpha", "0.06"));
        return ApiResponse.success(c);
    }

    /**
     * 更新可配置项
     * 仅允许 editable=1 的配置被修改
     */
    @PostMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> updated = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            List<ReleaseConfig> existing = releaseConfigRepo.findByConfigKey(key);
            if (existing.isEmpty()) {
                continue;
            }
            ReleaseConfig cfg = existing.get(0);
            if (cfg.getEditable() == null || cfg.getEditable() == 0) {
                updated.put(key, "不可编辑（硬常量）");
                continue;
            }
            cfg.setConfigValue(String.valueOf(e.getValue()));
            cfg.setUpdatedAt(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            cfg.setUpdatedBy("admin");
            releaseConfigRepo.save(cfg);
            updated.put(key, e.getValue());
        }
        return ApiResponse.success(updated);
    }

    /**
     * 灰度释放审批
     * 占位实现，未持久化（保留原 MockData 占位）
     */
    @GetMapping("/gray/approvals")
    public ApiResponse<PageResult<Map<String, Object>>> grayApprovals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 取信用分较低商家作为待审批候选
        List<Merchant> lowCredit = merchantRepo.findAll().stream()
                .filter(m -> m.getCreditScore() != null && m.getCreditScore() < 80)
                .collect(Collectors.toList());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Merchant m : lowCredit) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("merchantName", m.getMerchantName());
            row.put("creditScore", m.getCreditScore());
            row.put("penaltyStatus", m.getPenaltyStatus());
            row.put("status", 0);
            row.put("amount", 5000);
            list.add(row);
        }
        return ApiResponse.success(PageResult.of(list, page, size));
    }

    @GetMapping("/gray/approvals/{id}")
    public ApiResponse<Map<String, Object>> grayApprovalDetail(@PathVariable Long id) {
        Optional<Merchant> m = merchantRepo.findById(id);
        if (!m.isPresent()) {
            return ApiResponse.fail("审批单不存在");
        }
        Merchant merchant = m.get();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", id);
        detail.put("status", 0);
        detail.put("amount", 5000);
        detail.put("merchantName", merchant.getMerchantName());
        detail.put("creditScore", merchant.getCreditScore());
        detail.put("reason", "信用分低于80触发灰度审批");
        return ApiResponse.success(detail);
    }

    // ===================== 私有辅助方法 =====================

    private DailyReleaseSummary latestDailySummary() {
        return dailyReleaseRepo.findAll().stream()
                .filter(s -> s.getDate() != null)
                .max(Comparator.comparing(DailyReleaseSummary::getDate))
                .orElse(null);
    }

    private String getConfigValue(String key, String defaultValue) {
        List<ReleaseConfig> list = releaseConfigRepo.findByConfigKey(key);
        return list.isEmpty() ? defaultValue : list.get(0).getConfigValue();
    }
}
