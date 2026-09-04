package com.lianshengtong.gateway.gray.rollout;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase N：Gateway 灰度发布自治理 Coordinator 的全局配置。
 * <p>
 * 所有字段都提供 Helm/环境变量覆盖，见 application.yml `gray.rollout.*`。
 */
@Data
@ConfigurationProperties("gray.rollout")
public class GrayRolloutProperties {

    /** 全局开关：false 时 Coordinator 不调度 tick，Spring 仍加载 bean 但不做写操作。 */
    private boolean enabled = true;

    /** @Scheduled fixedDelay（毫秒）。生产建议 15-30s，压测可缩到 2s。 */
    private long tickMs = 15_000L;

    /** 步进权重（默认 1→5→20→50→100）。若最后一步不等于 100，会在 Coordinator 初始化时自动补 100。 */
    private List<Integer> steps = new ArrayList<>(Arrays.asList(1, 5, 20, 50, 100));

    /** 每步"最短保持时间"（分钟）。保持期内即使 SLO 全 PASS 也不推进；用于确保真实流量充分。 */
    private int minMinutesAtStep = 5;

    /** 硬门限 1：canary 错误率 - baseline 错误率 ≤ X 个百分点（如 0.5 表示最多高 0.5%）。 */
    private double maxErrorDriftPct = 0.5d;

    /** 硬门限 2：canary_p95 / baseline_p95 ≤ X（1.3 表示 canary 可以慢 30%，不允许更差）。 */
    private double maxP95Ratio = 1.3d;

    /** 硬门限 3：canary 滚动样本数 ≥ N（默认 500）。低于视为"样本不足"——既不推进也不回滚，避免灰度刚上来的噪声误杀。 */
    private long minSamplesThreshold = 500L;

    /** 连续 N 个 tick 都 SLO_FAIL → 触发 rollback。防止一次瞬时抖动误杀。 */
    private int maxConsecutiveFailuresBeforeRollback = 2;

    /** Redisson 不可用时允许降级为 JVM 原子 CAS leader（单实例部署安全；多实例部署可能双写，DB 事务 + append-only history 兜底最终一致）。 */
    private boolean allowJvmLeaderFallback = true;

    /** Redisson 租约时长（毫秒）。默认 > tickMs，避免一次 tick 没做完就丢锁。 */
    private long leaderLeaseMs = 55_000L;

    /** 节点标识：默认用 POD_NAME env（K8s）→ HOSTNAME env → "gw-unknown"。仅用于日志和 `/rollout/status` 展示。 */
    private String nodeId = "";

    /** Coordinator 读取 Redis 聚合 stats 时允许的最大超时（毫秒）；超时视为 SLO 不可用，不推进不回滚。 */
    private long statsTimeoutMs = 2_000L;
}
