package com.lianshengtong.release.job;

import com.lianshengtong.common.result.R;
import com.lianshengtong.release.alert.AlertChannel;
import com.lianshengtong.release.feign.GrayGatewayClient;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Phase P - Gray release audit + stale policy cleanup XXL-JOB Handler.
 *
 * Configure 2 jobs in XXL-JOB admin console:
 * 1) grayAuditJob - cron: "0 0/10 * * * ?" (every 10 minutes)
 *    Calls gateway /rollout/status, detects:
 *      - coordinator stuck (last tick too old) -> P0 alert
 *      - per-policy consecutive SLO failures near threshold -> P1 alert
 *      - policies idling > 14 days (no graduation/no rollback) -> P2 alert
 * 2) grayStalePolicyEnforceJob - cron: "0 30 3 * * ?" (daily 03:30)
 *    Placeholder: hook DB queries then batch mark stale policies tombstone.
 *    Requires schema + approval flow integration; currently only logs candidates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayAuditJobHandler {

    private final GrayGatewayClient gatewayClient;
    /** AlertChannel 可插拔：默认 Logging；配置 alert.channel=feishu 时推送飞书卡片。 */
    private final AlertChannel alertChannel;

    @Value("${release.alert-receivers:admin-super-001,admin-super-002}")
    private String alertReceivers;

    /** Consecutive SLO failures >= this -> fire P1 warning (before hard rollback kicks in). */
    private final int warnConsecutiveFailures = Math.max(1,
            Integer.parseInt(System.getenv().getOrDefault("GRAY_AUDIT_WARN_CONSEC_FAIL", "2")));
    /** Leader tick age > this seconds -> treat as deadlock. */
    private final long staleTickSec = Long.parseLong(
            System.getenv().getOrDefault("GRAY_AUDIT_STALE_TICK_SEC", "180"));

    @XxlJob("grayAuditJob")
    public void grayAuditJob() {
        log.info("[gray-audit] start. warnConsecutiveFailures={}, staleTickSec={}",
                warnConsecutiveFailures, staleTickSec);
        try {
            R<Map<String, Object>> statusR = safeCall(gatewayClient::rolloutStatus);
            if (statusR == null || !statusR.isSuccess() || statusR.getData() == null) {
                XxlJobHelper.handleFail("gateway rollout/status call failed; check lsc-gateway reachability");
                return;
            }
            Map<String, Object> s = statusR.getData();
            boolean enabled = Boolean.TRUE.equals(s.get("coordinatorEnabled"));
            if (!enabled) {
                log.warn("[gray-audit] coordinator disabled; skip. payload={}", s);
                XxlJobHelper.log("coordinator disabled, nothing to audit");
                return;
            }
            long age = s.get("lastTickAgeSec") == null ? -1L : ((Number) s.get("lastTickAgeSec")).longValue();
            if (age > staleTickSec) {
                alert("P0", "GrayRolloutCoordinator stale tick", "lastTickAgeSec=" + age);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> perPolicy = (List<Map<String, Object>>) s.getOrDefault("policies", List.of());
            for (Map<String, Object> p : perPolicy) {
                Object failuresObj = p.get("consecutiveSloFailures");
                Object policyId = p.get("policyId");
                if (failuresObj instanceof Number n && n.intValue() >= warnConsecutiveFailures) {
                    alert("P1", "policy " + policyId + " SLO FAIL consecutive " + n.intValue() + " times",
                            "payload=" + p);
                }
                long maintainedSec = p.get("maintainedAtWeightSec") == null ? 0L
                        : ((Number) p.get("maintainedAtWeightSec")).longValue();
                // zombie policy: idle > 14 days
                if (maintainedSec > 14L * 24L * 3600L) {
                    alert("P2", "policy " + policyId + " stale for " + (maintainedSec / 86400L) + " days",
                            "recommend: manual advance-step / rollback / set rollout.enabled=false");
                }
            }
            XxlJobHelper.log("grayAuditJob done; policies scanned=" + perPolicy.size());
        } catch (Exception ex) {
            log.error("[gray-audit] job error: {}", ex.getMessage(), ex);
            XxlJobHelper.handleFail(ex.getMessage());
        }
    }

    @XxlJob("grayStalePolicyEnforceJob")
    public void grayStalePolicyEnforceJob() {
        log.info("[gray-audit] stale-policy enforce start");
        // TODO(backend+ops): integrate with real flow:
        // 1) DB query: gray_release_policy table filter by staleness rules
        // 2) Call gateway /policies/{id}/graduate or /policies/{id}/delete (or approval flow)
        // 3) Always append audit with operator=system:gray-audit
        XxlJobHelper.log("PLACEHOLDER: grayStalePolicyEnforceJob is no-op skeleton; enable batch actions after DB + approval integration.");
        XxlJobHelper.log("Candidates: GrayApprovalFlowMapper.staleCandidates(days=30) / .graduatedOver(days=15)");
    }

    // ---------- internals ----------
    @FunctionalInterface
    private interface SafeSupplier<T> { T get() throws Exception; }

    private <T> T safeCall(SafeSupplier<T> s) {
        try { return s.get(); }
        catch (Exception ex) {
            log.warn("[gray-audit] feign failed: {}", ex.getMessage());
            return null;
        }
    }

    /** Alert sink - routes to pluggable AlertChannel (default logs; optionally Feishu/Dingtalk/PagerDuty). */
    private void alert(String level, String title, String detail) {
        String fullTitle = "[" + level + "] " + title;
        String fullDetail = detail + "\n[source] grayAuditJob in lsc-release-service";
        log.error("[ALERT-{}] {}\n  detail: {}", level, title, detail);
        try {
            alertChannel.send(alertReceivers, fullTitle, fullDetail);
        } catch (Exception ex) {
            log.warn("[gray-audit] alert channel failed: {}", ex.getMessage());
        }
    }
}
