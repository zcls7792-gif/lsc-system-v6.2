package com.lianshengtong.gateway.gray.rollout;

import java.time.Duration;
import java.time.Instant;

/**
 * Phase N：单策略 rollout 运行态（内存，Coordinator 维护）。
 * <p>
 * 用于跟踪"进入该步进的时刻"（用来与 minMinutesAtStep 比较）、"连续 SLO 失败次数"（达到阈值触发硬回滚）。
 * 若策略 weight 不在 steps 序列里（如运维手动 setWeight(10)，而 steps=[1,5,20,50,100]），
 * Coordinator 会把 currentStepIndex 重定位为"最小 ≥ weight 的 step"，并把 enteredStepAt 重置为"now"。
 */
public class RolloutRuntimeState {
    public int currentStepIndex;
    public int currentStepWeight;
    public Instant enteredStepAt;
    public int consecutiveSloFailures;
    public int consecutiveSloPasses;
    /** 上次 SloResult 的摘要：便于 `/policies/{id}/rollout` 展示；不用于逻辑分支。 */
    public String lastSloSummary;
    public volatile Instant lastTickAt;
    /** 只读 Snapshot 返回给 Controller。 */
    public record Snapshot(
            int stepIndex,
            int stepWeight,
            long secondsAtStep,
            int consecutiveSloFailures,
            int consecutiveSloPasses,
            String lastSloSummary,
            long lastTickSecondsAgo
    ) {}

    public Snapshot snapshot(Instant now) {
        long sa = enteredStepAt == null ? 0L : Math.max(0L, Duration.between(enteredStepAt, now).getSeconds());
        long ago = lastTickAt == null ? 0L : Math.max(0L, Duration.between(lastTickAt, now).getSeconds());
        return new Snapshot(currentStepIndex, currentStepWeight, sa,
                consecutiveSloFailures, consecutiveSloPasses, lastSloSummary, ago);
    }
}
