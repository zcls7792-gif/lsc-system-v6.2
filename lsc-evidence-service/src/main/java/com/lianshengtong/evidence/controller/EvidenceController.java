package com.lianshengtong.evidence.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.evidence.dto.VerifyRequest;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.DailySnapshotRecord;
import com.lianshengtong.evidence.service.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "存证服务", description = "关键操作SHA-256哈希上链/每日Merkle快照/存证查询校验")
@Validated
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);

    private static final String HASH_PATTERN = "^0x[a-fA-F0-9]{64}$";
    private static final String BIZ_TYPE_PATTERN = "^[A-Z_]+$";
    private static final String BIZ_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
    private static final String DATE_PATTERN_STR = "^\\d{4}-\\d{2}-\\d{2}$";

    private final EvidenceService evidenceService;

    @Operation(summary = "保存存证(SHA-256哈希上链)")
    @PostMapping("/save")
    public R<String> save(
            @RequestParam("bizType")
            @NotBlank(message = "业务类型不能为空")
            @Size(max = 32, message = "业务类型长度不能超过32字符")
            @Pattern(regexp = BIZ_TYPE_PATTERN, message = "业务类型必须为大写字母和下划线组合")
            String bizType,
            @RequestParam("bizId")
            @NotBlank(message = "业务ID不能为空")
            @Size(max = 128, message = "业务ID长度不能超过128字符")
            @Pattern(regexp = BIZ_ID_PATTERN, message = "业务ID仅允许字母、数字、下划线和连字符")
            String bizId,
            @RequestParam(value = "dataHash", required = false)
            @Size(max = 128, message = "数据哈希长度不能超过128字符")
            @Pattern(regexp = HASH_PATTERN, message = "数据哈希格式不合法，应为0x开头的64位十六进制字符串")
            String dataHash,
            @RequestParam(value = "payload", required = false)
            @Size(max = 10000, message = "载荷数据长度不能超过10000字符")
            String payload,
            HttpServletRequest request) {
        String currentUser = (String) request.getAttribute("currentUser");
        log.info("存证操作: user={}, bizType={}, bizId={}, dataHash={}", currentUser, bizType, bizId, dataHash);
        return R.ok(evidenceService.saveEvidence(bizType, bizId, dataHash, payload));
    }

    @Operation(summary = "每日快照存证(Merkle树根上链)")
    @PostMapping("/snapshot")
    public R<DailySnapshotRecord> snapshot(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate date,
            HttpServletRequest request) {
        String currentUser = (String) request.getAttribute("currentUser");
        log.info("快照存证操作: user={}, date={}", currentUser, date);
        return R.ok(evidenceService.dailySnapshot(date));
    }

    @Operation(summary = "查询存证记录")
    @GetMapping("/query")
    public R<List<BlockchainRecord>> query(
            @RequestParam(value = "bizType", required = false)
            @Size(max = 32, message = "业务类型长度不能超过32字符")
            @Pattern(regexp = BIZ_TYPE_PATTERN, message = "业务类型必须为大写字母和下划线组合")
            String bizType,
            @RequestParam(value = "bizId", required = false)
            @Size(max = 128, message = "业务ID长度不能超过128字符")
            @Pattern(regexp = BIZ_ID_PATTERN, message = "业务ID仅允许字母、数字、下划线和连字符")
            String bizId) {
        return R.ok(evidenceService.query(bizType, bizId));
    }

    @Operation(summary = "存证记录分页列表(管理后台)")
    @GetMapping("/list")
    public R<IPage<BlockchainRecord>> list(
            @RequestParam(value = "page", required = false, defaultValue = "1")
            @Min(value = 1, message = "页码不能小于1")
            Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20")
            @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 100, message = "每页条数不能超过100")
            Integer size,
            @RequestParam(value = "batchNo", required = false)
            @Size(max = 64, message = "批次号长度不能超过64字符")
            String batchNo,
            @RequestParam(value = "hash", required = false)
            @Size(max = 128, message = "哈希值长度不能超过128字符")
            String hash,
            @RequestParam(value = "txId", required = false)
            @Size(max = 128, message = "交易哈希长度不能超过128字符")
            String txId,
            @RequestParam(value = "startDate", required = false)
            @Pattern(regexp = DATE_PATTERN_STR, message = "开始日期格式应为yyyy-MM-dd")
            String startDate,
            @RequestParam(value = "endDate", required = false)
            @Pattern(regexp = DATE_PATTERN_STR, message = "结束日期格式应为yyyy-MM-dd")
            String endDate,
            HttpServletRequest request) {
        String currentUser = (String) request.getAttribute("currentUser");
        log.info("查询存证列表: user={}, page={}, size={}", currentUser, page, size);
        return R.ok(evidenceService.listPage(page, size, batchNo, hash, txId, startDate, endDate));
    }

    @Operation(summary = "存证记录详情")
    @GetMapping("/{id}")
    public R<BlockchainRecord> detail(
            @PathVariable("id")
            @Positive(message = "存证ID必须为正数")
            Long id) {
        return R.ok(evidenceService.getById(id));
    }

    @Operation(summary = "按日期校验存证")
    @GetMapping("/verify/{date}")
    public R<Boolean> verify(
            @PathVariable("date")
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate date) {
        return R.ok(evidenceService.verify(date));
    }

    @Operation(summary = "触发存证校验(POST)")
    @PostMapping("/verify")
    public R<Boolean> verifyPost(@Valid @RequestBody VerifyRequest request) {
        LocalDate date = request.getDate() == null ? LocalDate.now() : request.getDate();
        return R.ok(evidenceService.verify(date));
    }

    @Operation(summary = "按日期校验存证报告(管理后台)")
    @GetMapping("/verify-report")
    public R<Map<String, Object>> verifyReport(
            @RequestParam("date")
            @Pattern(regexp = DATE_PATTERN_STR, message = "日期格式应为yyyy-MM-dd")
            String date,
            HttpServletRequest request) {
        String currentUser = (String) request.getAttribute("currentUser");
        log.info("校验报告查询: user={}, date={}", currentUser, date);
        return R.ok(evidenceService.verifyReport(LocalDate.parse(date)));
    }

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    public EvidenceService getEvidenceService() { return evidenceService; }
}
