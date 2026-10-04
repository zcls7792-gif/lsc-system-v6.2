package com.lianshengtong.risk.service;

import cn.hutool.core.util.IdUtil;
import com.lianshengtong.ledger.service.LscLedgerService;
import com.lianshengtong.risk.entity.RiskCase;
import com.lianshengtong.risk.mapper.RiskCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 风控服务（V7.7.2 第十章）
 * AI 标记后 48 小时内人工复核，逾期升级。
 * 冻结必须带 case_id、原因、范围、到期复查时间。
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskCaseMapper caseMapper;
    private final LscLedgerService ledgerService;

    /** 创建风控案件 */
    @Transactional(rollbackFor = Exception.class)
    public RiskCase createCase(Long userId, String ruleId, boolean aiFlag,
                                String evidenceRef, String proposedAction) {
        RiskCase c = new RiskCase();
        c.setCaseId(IdUtil.getSnowflakeNextId());
        c.setUserId(userId);
        c.setRuleId(ruleId);
        c.setAiFlag(aiFlag);
        c.setEvidenceRef(evidenceRef);
        c.setProposedAction(proposedAction);
        c.setReviewStatus("PENDING");
        c.setReviewDeadline(LocalDateTime.now().plusHours(48));
        c.setCreatedAt(LocalDateTime.now());
        caseMapper.insert(c);
        return c;
    }

    /** 人工决定冻结权益 */
    @Transactional(rollbackFor = Exception.class)
    public void freezeLsc(Long caseId, Long userId, String sourceBucket,
                           Long grantLotId, Long availableLotId, long freezeUnit) {
        RiskCase c = caseMapper.selectById(caseId);
        if (c == null) return;
        c.setReviewStatus("REVIEWED");
        c.setDecision("FREEZE");
        c.setDecidedAt(LocalDateTime.now());
        caseMapper.updateById(c);

        ledgerService.freeze(userId, caseId, sourceBucket, grantLotId, availableLotId,
                freezeUnit, "FREEZE_CASE_" + caseId);
    }
}
