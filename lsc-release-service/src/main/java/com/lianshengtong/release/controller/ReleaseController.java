package com.lianshengtong.release.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.security.RequireAdminRole;
import com.lianshengtong.release.dto.AiReleasePredictDTO;
import com.lianshengtong.release.dto.ParamApprovalDTO;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.entity.ParamChangeApproval;
import com.lianshengtong.release.entity.ReleaseConfig;
import com.lianshengtong.release.feign.AiGatewayFeignClient;
import com.lianshengtong.release.mapper.DailyReleaseSummaryMapper;
import com.lianshengtong.release.service.BatchReleaseService;
import com.lianshengtong.release.service.ReleaseCalcService;
import com.lianshengtong.release.service.ReleaseConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 释放服务控制器
 * <p>
 * 提供手动触发释放、汇总查询、配置管理、参数变更审批等接口。
 * </p>
 */
@Slf4j
@Tag(name = "释放服务", description = "释放计算/批量执行/配置与参数审批")
@RestController
@RequestMapping("/api/release")
@RequiredArgsConstructor
@RequireAdminRole(1)
public class ReleaseController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReleaseCalcService releaseCalcService;
    private final BatchReleaseService batchReleaseService;
    private final ReleaseConfigService releaseConfigService;
    private final DailyReleaseSummaryMapper dailyReleaseSummaryMapper;
    private final AiGatewayFeignClient aiGatewayFeignClient;

    @Operation(summary = "手动触发释放(需管理员权限)")
    @PostMapping("/execute")
    @RequireAdminRole(2)
    public R<DailyReleaseSummary> execute(@Valid @RequestBody DailyReleaseSummary input,
                                          @RequestHeader(value = "X-Admin-Id", required = false) String adminId) {
        // 权限校验：网关解析 Admin JWT 后填充 X-Admin-Id，未携带或为空即视为未授权
        // 网关白名单错误或被绕过时，此校验仍可阻挡未授权调用
        if (adminId == null || adminId.isBlank()) {
            log.warn("[ReleaseExecute] 拒绝未授权的手动触发请求");
            return R.fail(401, "未授权：缺少管理员身份标识");
        }
        log.info("[ReleaseExecute] 手动触发释放 operator={} date={}", adminId, input.getDate());
        if (input.getDate() == null) {
            input.setDate(LocalDate.now());
        }
        // 核心算法计算
        releaseCalcService.calcDailyRelease(input);
        // 批量释放(断点续跑 + 汇总校验)
        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(input);
        return R.ok(result);
    }

    @Operation(summary = "查询某日释放汇总")
    @GetMapping("/summary/{date}")
    public R<DailyReleaseSummary> summary(@PathVariable("date") String date) {
        LocalDate d = LocalDate.parse(date, DATE_FMT);
        DailyReleaseSummary summary = dailyReleaseSummaryMapper.findByDate(d);
        return R.ok(summary);
    }

    @Operation(summary = "查询释放配置列表")
    @GetMapping("/config")
    public R<List<ReleaseConfig>> config() {
        return R.ok(releaseConfigService.listAll());
    }

    @Operation(summary = "参数变更申请(editable=1)")
    @PostMapping("/param-approval")
    public R<ParamChangeApproval> paramApproval(@Valid @RequestBody ParamApprovalDTO.ApplyRequest request) {
        ParamChangeApproval approval = releaseConfigService.applyParamChange(
                request.getConfigKey(), request.getConfigValue(),
                request.getOperator(), request.getEvidenceTxHash());
        return R.ok(approval);
    }

    @Operation(summary = "参数变更审批(双人审批)")
    @PostMapping("/param-approve")
    public R<ReleaseConfig> paramApprove(@Valid @RequestBody ParamApprovalDTO.ApproveRequest request) {
        boolean approved = request.getApproved() == null ? false : request.getApproved();
        ReleaseConfig config = releaseConfigService.approveParamChange(
                request.getApprovalId(), request.getApprover(),
                request.getApproverSignatures(), request.getApproveComment(), approved);
        return R.ok(config);
    }

    @Operation(summary = "释放汇总范围分页查询(管理后台)")
    @GetMapping("/summary")
    public R<IPage<DailyReleaseSummary>> summaryRange(@RequestParam(required = false, defaultValue = "1") Integer page,
                                                       @RequestParam(required = false, defaultValue = "20") Integer size,
                                                       @RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate,
                                                       @RequestParam(required = false) Integer status) {
        Page<DailyReleaseSummary> p = new Page<>(page, size);
        LambdaQueryWrapper<DailyReleaseSummary> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge(DailyReleaseSummary::getDate, LocalDate.parse(startDate, DATE_FMT));
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le(DailyReleaseSummary::getDate, LocalDate.parse(endDate, DATE_FMT));
        }
        if (status != null) {
            wrapper.eq(DailyReleaseSummary::getStatus, status);
        }
        wrapper.orderByDesc(DailyReleaseSummary::getDate);
        return R.ok(dailyReleaseSummaryMapper.selectPage(p, wrapper));
    }

    @Operation(summary = "AI释放趋势预测(转发 AI 网关)")
    @GetMapping("/predict")
    public R<Map<String, Object>> predict(@RequestParam(required = false, defaultValue = "7") Integer days) {
        // 拉取最近30天历史核销率序列作为 AI 预测输入
        LambdaQueryWrapper<DailyReleaseSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(DailyReleaseSummary::getK)
                .orderByDesc(DailyReleaseSummary::getDate)
                .last("LIMIT 30");
        List<DailyReleaseSummary> recent = dailyReleaseSummaryMapper.selectList(wrapper);
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("fallback", true);
        fallback.put("message", "AI预测服务暂不可用,历史数据不足");
        if (recent.isEmpty()) {
            return R.ok(fallback);
        }
        // 按日期升序整理历史序列
        List<DailyReleaseSummary> asc = new ArrayList<>(recent);
        java.util.Collections.reverse(asc);
        List<java.math.BigDecimal> kSeries = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        for (DailyReleaseSummary s : asc) {
            if (s.getK() != null) {
                kSeries.add(s.getK());
                dates.add(s.getDate());
            }
        }
        AiReleasePredictDTO.Request req = AiReleasePredictDTO.Request.builder()
                .startDate(LocalDate.now())
                .historyKSeries(kSeries)
                .historyDates(dates)
                .predictDays(days)
                .build();
        try {
            R<AiReleasePredictDTO.Response> resp = aiGatewayFeignClient.releasePredict(req);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("predictedK7d", resp.getData().getPredictedK7d());
                data.put("predictedK30d", resp.getData().getPredictedK30d());
                data.put("confidence", resp.getData().getConfidence());
                data.put("trend", resp.getData().getTrend());
                data.put("fallback", resp.getData().getFallback());
                return R.ok(data);
            }
        } catch (RuntimeException e) {
            log.warn("[ReleasePredict] AI网关调用失败,降级返回", e);
        }
        return R.ok(fallback);
    }

    @Operation(summary = "参数仿真推演(转发 AI 网关)")
    @PostMapping("/simulation")
    public R<Map<String, Object>> simulation(@RequestBody Map<String, Object> body) {
        try {
            R<Map<String, Object>> resp = aiGatewayFeignClient.paramSimulation(body);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return R.ok(resp.getData());
            }
        } catch (RuntimeException e) {
            log.warn("[ReleaseSimulation] AI网关调用失败,降级返回", e);
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("fallback", true);
        fallback.put("message", "AI仿真服务暂不可用");
        return R.ok(fallback);
    }

    @Operation(summary = "释放趋势图表数据(按日聚合最近N天)")
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(required = false, defaultValue = "30") Integer days) {
        LambdaQueryWrapper<DailyReleaseSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(DailyReleaseSummary::getDate, LocalDate.now().minusDays(days))
                .orderByDesc(DailyReleaseSummary::getDate);
        List<DailyReleaseSummary> list = dailyReleaseSummaryMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        // 倒序转正序, 便于前端图表绘制
        java.util.Collections.reverse(list);
        for (DailyReleaseSummary s : list) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", s.getDate() == null ? null : s.getDate().toString());
            point.put("rate", s.getRate());
            point.put("k", s.getK());
            point.put("tRelease", s.getTRelease());
            point.put("lLocked", s.getLLocked());
            point.put("mTotal", s.getMTotal());
            point.put("nTotal", s.getNTotal());
            result.add(point);
        }
        return R.ok(result);
    }
}
