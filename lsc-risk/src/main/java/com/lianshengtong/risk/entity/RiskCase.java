package com.lianshengtong.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_case")
public class RiskCase {
    @TableId(type = IdType.INPUT)
    private Long caseId;
    private Long userId;
    private String ruleId;
    private Boolean aiFlag;
    private String evidenceRef;
    private String proposedAction;
    private String reviewStatus;  // PENDING/REVIEWED/ESCALATED
    private LocalDateTime reviewDeadline;
    private Long reviewerId;
    private String decision;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
