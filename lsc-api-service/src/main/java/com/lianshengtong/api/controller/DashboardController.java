package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.entity.DailyReleaseSummary;
import com.lianshengtong.api.entity.LscAccount;
import com.lianshengtong.api.entity.Merchant;
import com.lianshengtong.api.entity.Order;
import com.lianshengtong.api.entity.ReleaseConfig;
import com.lianshengtong.api.entity.Writeoff;
import com.lianshengtong.api.repository.DailyReleaseSummaryRepository;
import com.lianshengtong.api.repository.LscAccountRepository;
import com.lianshengtong.api.repository.MerchantRepository;
import com.lianshengtong.api.repository.OrderRepository;
import com.lianshengtong.api.repository.ReleaseConfigRepository;
import com.lianshengtong.api.repository.WriteoffRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 仪表盘控制器（V6.2 更新）
 * 数据源切换：使用 H2 持久化数据替代 MockData 硬编码
 * - 商家/订单/核销统计来自 Repository 实时聚合
 * - LSC 释放趋势来自 DailyReleaseSummary 表
 * - LSC 账户余额来自 LscAccount 表
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MerchantRepository merchantRepo;
    private final OrderRepository orderRepo;
    private final WriteoffRepository writeoffRepo;
    private final LscAccountRepository lscAccountRepo;
    private final DailyReleaseSummaryRepository dailyReleaseRepo;
    private final ReleaseConfigRepository releaseConfigRepo;

    public DashboardController(MerchantRepository merchantRepo,
                              OrderRepository orderRepo,
                              WriteoffRepository writeoffRepo,
                              LscAccountRepository lscAccountRepo,
                              DailyReleaseSummaryRepository dailyReleaseRepo,
                              ReleaseConfigRepository releaseConfigRepo) {
        this.merchantRepo = merchantRepo;
        this.orderRepo = orderRepo;
        this.writeoffRepo = writeoffRepo;
        this.lscAccountRepo = lscAccountRepo;
        this.dailyReleaseRepo = dailyReleaseRepo;
        this.releaseConfigRepo = releaseConfigRepo;
    }

    /**
     * 全局概览
     * - 商家数：merchantRepo.count()
     * - 订单数：orderRepo.count()
     * - 已核销LSC：writeoffRepo 聚合 lscAmount
     * - 可用LSC总量：lscAccountRepo 聚合 totalAvailable
     * - 锁定LSC总量：lscAccountRepo 聚合 totalLocked
     * - 核销率k：取最新 DailyReleaseSummary.k
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> d = new LinkedHashMap<>();

        long merchantCount = merchantRepo.count();
        long orderCount = orderRepo.count();

        // 聚合 LSC 账户
        long totalLocked = lscAccountRepo.findAll().stream()
                .mapToLong(LscAccount::getTotalLocked).sum();
        long totalAvailable = lscAccountRepo.findAll().stream()
                .mapToLong(LscAccount::getTotalAvailable).sum();
        long totalLsc = totalLocked + totalAvailable;

        // 聚合订单交易额
        double totalTransaction = orderRepo.findAll().stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
                .sum();

        // 聚合核销 LSC
        double totalWriteoffLsc = writeoffRepo.findAll().stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        // 今日数据
        String today = LocalDate.now().toString();
        long todayOrder = orderRepo.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().startsWith(today))
                .count();
        double todayTransaction = orderRepo.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().startsWith(today))
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
                .sum();
        double todayLsc = writeoffRepo.findAll().stream()
                .filter(w -> w.getCreatedAt() != null && w.getCreatedAt().startsWith(today))
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        // 核销率k（取最新日汇总）
        DailyReleaseSummary latest = latestDailySummary();
        double writeoffRateK = latest != null && latest.getK() != null ? latest.getK() : 0.0;

        long activeMerchants = merchantRepo.findAll().stream()
                .filter(m -> m.getAuditStatus() != null && m.getAuditStatus() == 1)
                .count();

        d.put("merchantCount", merchantCount);
        d.put("orderCount", orderCount);
        d.put("totalTransaction", Math.round(totalTransaction * 100) / 100.0);
        d.put("totalLsc", totalLsc);
        d.put("totalLocked", totalLocked);
        d.put("totalAvailable", totalAvailable);
        d.put("totalWriteoffLsc", Math.round(totalWriteoffLsc * 100) / 100.0);
        d.put("todayOrder", todayOrder);
        d.put("todayTransaction", Math.round(todayTransaction * 100) / 100.0);
        d.put("todayLsc", Math.round(todayLsc * 100) / 100.0);
        // 核销率k以百分比展示（保留两位小数）
        d.put("writeoffRate", String.format("%.2f%%", writeoffRateK * 100));
        d.put("activeMerchants", activeMerchants);
        // 用户数沿用 LSC 账户数（每账户对应一个 userId）
        d.put("userCount", lscAccountRepo.count());
        return ApiResponse.success(d);
    }

    /**
     * 释放趋势
     * 数据源：DailyReleaseSummary 表（按日期升序返回最近 days 天）
     */
    @GetMapping("/release-trend")
    public ApiResponse<List<Map<String, Object>>> releaseTrend(@RequestParam(defaultValue = "7") int days) {
        List<DailyReleaseSummary> all = dailyReleaseRepo.findAll();
        all.sort(Comparator.comparing(DailyReleaseSummary::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // 取最近 days 条
        List<DailyReleaseSummary> tail = all.size() > days
                ? all.subList(all.size() - days, all.size())
                : all;

        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (DailyReleaseSummary s : tail) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", s.getDate() != null ? s.getDate()
                    : LocalDate.now().minusDays(tail.size() - tail.indexOf(s) - 1L).toString());
            row.put("released", s.getTRelease() != null ? s.getTRelease() : 0);
            row.put("writeoff", s.getNTotal() != null ? Math.round(s.getNTotal()) : 0);
            row.put("k", s.getK() != null ? s.getK() : 0);
            row.put("rate", s.getRate() != null ? s.getRate() : 0);
            trend.add(row);
        }
        return ApiResponse.success(trend);
    }

    /**
     * 核销率
     * 数据源：DailyReleaseSummary 最新记录 + Writeoff 聚合
     */
    @GetMapping("/writeoff-rate")
    public ApiResponse<Map<String, Object>> writeoffRate() {
        DailyReleaseSummary latest = latestDailySummary();

        double totalWriteoff = writeoffRepo.findAll().stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        long totalReleased = latest != null && latest.getLLocked() != null
                ? latest.getLLocked() : 0;

        double k = latest != null && latest.getK() != null ? latest.getK() : 0.0;
        double rate = latest != null && latest.getRate() != null ? latest.getRate() : 0.0;

        // 今日/本周/本月聚合
        String today = LocalDate.now().toString();
        String weekStart = LocalDate.now().minusDays(6).toString();
        String monthStart = LocalDate.now().withDayOfMonth(1).toString();

        double todayWriteoff = writeoffRepo.findAll().stream()
                .filter(w -> w.getCreatedAt() != null && w.getCreatedAt().startsWith(today))
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        double weekWriteoff = writeoffRepo.findAll().stream()
                .filter(w -> w.getCreatedAt() != null && w.getCreatedAt().compareTo(weekStart) >= 0)
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        double monthWriteoff = writeoffRepo.findAll().stream()
                .filter(w -> w.getCreatedAt() != null && w.getCreatedAt().compareTo(monthStart) >= 0)
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalWriteoff", Math.round(totalWriteoff * 100) / 100.0);
        r.put("totalReleased", totalReleased);
        r.put("rate", String.format("%.2f%%", k * 100));
        r.put("releaseRate", String.format("%.4f%%", rate * 100));
        r.put("todayWriteoff", Math.round(todayWriteoff * 100) / 100.0);
        r.put("weekWriteoff", Math.round(weekWriteoff * 100) / 100.0);
        r.put("monthWriteoff", Math.round(monthWriteoff * 100) / 100.0);
        return ApiResponse.success(r);
    }

    /**
     * 商家侧统计
     * 参数 merchantId（默认取首个商家）
     * 数据源：Merchant + Order + Writeoff + LscAccount + ReleaseConfig
     */
    @GetMapping("/merchant/stats")
    public ApiResponse<Map<String, Object>> merchantStats(
            @RequestParam(required = false) Long merchantId) {
        Map<String, Object> s = new LinkedHashMap<>();

        // 默认取首个商家
        if (merchantId == null) {
            Optional<Merchant> first = merchantRepo.findAll().stream().findFirst();
            if (first.isPresent()) {
                merchantId = first.get().getId();
            }
        }
        final Long mid = merchantId;

        // 今日订单/营业额
        String today = LocalDate.now().toString();
        List<Order> todayOrders = orderRepo.findAll().stream()
                .filter(o -> mid == null || (o.getMerchantId() != null && o.getMerchantId().equals(mid)))
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().startsWith(today))
                .collect(java.util.stream.Collectors.toList());

        double todayRevenue = todayOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
                .sum();

        // 周/月聚合
        String weekStart = LocalDate.now().minusDays(6).toString();
        String monthStart = LocalDate.now().withDayOfMonth(1).toString();
        List<Order> weekOrders = orderRepo.findAll().stream()
                .filter(o -> mid == null || (o.getMerchantId() != null && o.getMerchantId().equals(mid)))
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().compareTo(weekStart) >= 0)
                .collect(java.util.stream.Collectors.toList());
        List<Order> monthOrders = orderRepo.findAll().stream()
                .filter(o -> mid == null || (o.getMerchantId() != null && o.getMerchantId().equals(mid)))
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().compareTo(monthStart) >= 0)
                .collect(java.util.stream.Collectors.toList());

        double weekRevenue = weekOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
                .sum();
        double monthRevenue = monthOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
                .sum();

        // LSC 账户
        Optional<LscAccount> account = mid != null ? lscAccountRepo.findById(mid) : Optional.empty();
        long availableLsc = account.map(LscAccount::getTotalAvailable).orElse(0L);

        // 商家信用分
        Optional<Merchant> m = mid != null ? merchantRepo.findById(mid) : Optional.empty();
        int creditScore = m.map(Merchant::getCreditScore).orElse(0);

        s.put("merchantId", mid);
        s.put("merchantName", m.map(Merchant::getMerchantName).orElse(""));
        s.put("todayOrders", todayOrders.size());
        s.put("todayRevenue", Math.round(todayRevenue * 100) / 100.0);
        s.put("availableLsc", availableLsc);
        s.put("totalLocked", account.map(LscAccount::getTotalLocked).orElse(0L));
        s.put("creditScore", creditScore);
        s.put("weekRevenue", Math.round(weekRevenue * 100) / 100.0);
        s.put("weekOrders", weekOrders.size());
        s.put("monthRevenue", Math.round(monthRevenue * 100) / 100.0);
        s.put("monthOrders", monthOrders.size());

        // 释放配置参考值
        Optional<ReleaseConfig> rateMax = releaseConfigRepo.findByConfigKey("rate_max").stream().findFirst();
        rateMax.ifPresent(c -> s.put("rateMax", c.getConfigValue()));
        return ApiResponse.success(s);
    }

    /**
     * 商家周趋势
     * 数据源：Order 表按日聚合
     */
    @GetMapping("/merchant/week-trend")
    public ApiResponse<List<Map<String, Object>>> merchantWeekTrend(
            @RequestParam(required = false) Long merchantId) {
        final Long mid = merchantId;
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            String dayStr = LocalDate.now().minusDays(i).toString();
            final String dayKey = dayStr;
            List<Order> dayOrders = orderRepo.findAll().stream()
                    .filter(o -> mid == null || (o.getMerchantId() != null && o.getMerchantId().equals(mid)))
                    .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().startsWith(dayKey))
                    .collect(java.util.stream.Collectors.toList());
            double revenue = dayOrders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                    .mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
                    .sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dayStr);
            row.put("orders", dayOrders.size());
            row.put("revenue", Math.round(revenue * 100) / 100.0);
            trend.add(row);
        }
        return ApiResponse.success(trend);
    }

    /**
     * 取最新一条 DailyReleaseSummary（按 date 倒序）
     */
    private DailyReleaseSummary latestDailySummary() {
        return dailyReleaseRepo.findAll().stream()
                .filter(s -> s.getDate() != null)
                .max(Comparator.comparing(DailyReleaseSummary::getDate))
                .orElse(null);
    }
}
