package com.lianshengtong.risk.controller;

import cn.hutool.core.util.IdUtil;
import com.lianshengtong.common.result.R;
import com.lianshengtong.risk.entity.Appeal;
import com.lianshengtong.risk.entity.RiskCase;
import com.lianshengtong.risk.mapper.AppealMapper;
import com.lianshengtong.risk.mapper.RiskCaseMapper;
import com.lianshengtong.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 风控接口（V7.7.2 第十章）
 */
@RestController
@RequestMapping("/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;
    private final AppealMapper appealMapper;
    private final RiskCaseMapper caseMapper;

    /** 创建风控案件 */
    @PostMapping("/cases")
    public R<RiskCase> createCase(@RequestParam Long userId,
                                   @RequestParam String ruleId,
                                   @RequestParam boolean aiFlag,
                                   @RequestParam(required = false) String evidenceRef,
                                   @RequestParam String proposedAction) {
        return R.ok(riskService.createCase(userId, ruleId, aiFlag, evidenceRef, proposedAction));
    }

    /** 人工决定冻结权益 */
    @PostMapping("/cases/{caseId}/freeze")
    public R<Void> freezeLsc(@PathVariable Long caseId,
                              @RequestParam Long userId,
                              @RequestParam String sourceBucket,
                              @RequestParam(required = false) Long grantLotId,
                              @RequestParam(required = false) Long availableLotId,
                              @RequestParam Long freezeUnit) {
        riskService.freezeLsc(caseId, userId, sourceBucket, grantLotId, availableLotId, freezeUnit);
        return R.ok();
    }

    /** 用户申诉 */
    @PostMapping("/appeals")
    public R<Map<String, Object>> createAppeal(@RequestBody Map<String, Object> body) {
        Long caseId = Long.valueOf(body.get("caseId").toString());
        Long userId = Long.valueOf(body.get("userId").toString());
        String reason = body.get("reason") != null ? body.get("reason").toString() : "";
        String evidenceRef = body.get("evidenceRef") != null ? body.get("evidenceRef").toString() : null;

        RiskCase riskCase = caseMapper.selectById(caseId);
        if (riskCase == null) return R.fail(404, "案件不存在");

        Appeal appeal = new Appeal();
        appeal.setAppealId(IdUtil.getSnowflakeNextId());
        appeal.setCaseId(caseId);
        appeal.setUserId(userId);
        appeal.setSubmittedAt(LocalDateTime.now());
        appeal.setReplyDeadline(LocalDateTime.now().plusDays(5));
        appeal.setStatus("PENDING");
        appealMapper.insert(appeal);

        Map<String, Object> data = new HashMap<>();
        data.put("appealId", appeal.getAppealId());
        data.put("status", "PENDING");
        data.put("replyDeadline", appeal.getReplyDeadline().toString());
        return R.ok(data);
    }

    /** 查询案件详情（含申诉入口） */
    @GetMapping("/cases/{caseId}")
    public R<RiskCase> getCase(@PathVariable Long caseId) {
        return R.ok(caseMapper.selectById(caseId));
    }
}
