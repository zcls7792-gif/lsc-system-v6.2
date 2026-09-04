package com.lianshengtong.release.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 灰度审批工作流参数配置。
 * <p>
 * 对应 application.yml 中 {@code gray.approval.*} 前缀。
 * </p>
 * <pre>
 * gray:
 *   approval:
 *     default-required-approvals: 2
 *     approver-role: ROLE_RELEASE_ADMIN
 *     execute-retry-max: 3
 *     audit-chain-enabled: false
 *     stale-executing-seconds: 120
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gray.approval")
public class GrayApprovalProperties {

    /** 默认需要的审批人数（create 未显式指定 requiredApprovals 时生效）。 */
    private int defaultRequiredApprovals = 2;

    /** 默认审批人角色 —— 审批人池来源（未在 CreateRequest.approvers 中显式指定时生效）。 */
    private String approverRole = "ROLE_RELEASE_ADMIN";

    /** 自愈任务中 EXECUTE_FAILED 允许的最大自动重试次数。 */
    private int executeRetryMax = 3;

    /** 是否启用审计流水的链上存证（默认关闭，需合规团队配置）。 */
    private boolean auditChainEnabled = false;

    /** 自愈任务：EXECUTING 状态持续超过该秒数视为"卡住"，回查网关真实状态。 */
    private long staleExecutingSeconds = 120L;

    /** 自愈任务两次自动重试之间的最小间隔（秒），避免高频重试打爆网关。 */
    private long selfHealRetryIntervalSeconds = 600L;

    /** 审批人超过 N 小时未处理提醒（XXL-JOB 发送飞书提醒卡片）。 */
    private long approverIdleRemindHours = 24L;
}
