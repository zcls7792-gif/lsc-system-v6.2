package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.task.BackfillService;
import com.zcls.lsc.task.BackfillService.BackfillRow;
import com.zcls.lsc.task.CronJobService;
import com.zcls.lsc.task.CronJobService.TaskRunInfo;
import com.zcls.lsc.task.DailySnapshotService;
import com.zcls.lsc.task.DailySnapshotService.SnapshotRow;
import com.zcls.lsc.task.InboxService;
import com.zcls.lsc.task.InboxService.InboxRow;
import com.zcls.lsc.task.LeaseService;
import com.zcls.lsc.task.LeaseService.LeaseInfo;
import com.zcls.lsc.task.LeaseService.LeaseResult;
import com.zcls.lsc.task.OutboxService;
import com.zcls.lsc.task.OutboxService.OutboxRow;
import com.zcls.lsc.task.enums.TaskEnums.BackfillSourceType;
import com.zcls.lsc.task.enums.TaskEnums.TaskType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 第13章 任务调度与可靠性接口（管理端）。
 *
 *  - 租约：获取 / 续约 / 释放 / 校验 / 巡检
 *  - Outbox：写入 / 拉取 / 标记成功失败 / 重投
 *  - Inbox：消费登记 / 标记 / 重投
 *  - 补单：创建 / 拉取 / 标记 / 从失败来源自动创建 / 人工激活
 *  - 日终快照：生成 / 查询 / 校验
 *  - CronJob：启动 / 游标更新 / 完成 / 失败 / 续租 / 查询
 */
@RestController
@RequestMapping("/v1/admin")
public class TaskController {

    private final LeaseService leaseService;
    private final OutboxService outboxService;
    private final InboxService inboxService;
    private final BackfillService backfillService;
    private final DailySnapshotService snapshotService;
    private final CronJobService cronJobService;

    public TaskController(LeaseService leaseService, OutboxService outboxService,
                         InboxService inboxService, BackfillService backfillService,
                         DailySnapshotService snapshotService, CronJobService cronJobService) {
        this.leaseService = leaseService;
        this.outboxService = outboxService;
        this.inboxService = inboxService;
        this.backfillService = backfillService;
        this.snapshotService = snapshotService;
        this.cronJobService = cronJobService;
    }

    // ===== 租约 =====

    @PostMapping("/leases/{key}/acquire")
    public ApiResponse<LeaseResult> acquireLease(
            @PathVariable String key,
            @RequestParam String owner,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        return ApiResponse.ok(leaseService.tryAcquire(key, owner, ttlSeconds));
    }

    @PostMapping("/leases/{key}/renew")
    public ApiResponse<Long> renewLease(
            @PathVariable String key,
            @RequestParam String owner,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        return ApiResponse.ok(leaseService.renew(key, owner, ttlSeconds));
    }

    @PostMapping("/leases/{key}/release")
    public ApiResponse<Void> releaseLease(
            @PathVariable String key, @RequestParam String owner) {
        leaseService.release(key, owner);
        return ApiResponse.ok(null);
    }

    @PostMapping("/leases/{key}/force-release")
    public ApiResponse<Void> forceReleaseLease(@PathVariable String key) {
        leaseService.forceRelease(key);
        return ApiResponse.ok(null);
    }

    @GetMapping("/leases/{key}/verify")
    public ApiResponse<Boolean> verifyLease(
            @PathVariable String key,
            @RequestParam String owner,
            @RequestParam long fencingToken) {
        return ApiResponse.ok(leaseService.isHeld(key, owner, fencingToken));
    }

    @GetMapping("/leases")
    public ApiResponse<List<LeaseInfo>> listLeases() {
        return ApiResponse.ok(leaseService.listActiveLeases());
    }

    // ===== Outbox =====

    @PostMapping("/outbox")
    public ApiResponse<Long> writeOutbox(
            @RequestParam long eventId,
            @RequestParam String topic,
            @RequestParam String aggregateId,
            @RequestParam long aggregateVersion,
            @RequestBody String payloadJson,
            @RequestParam(defaultValue = "1") int schemaVersion) {
        return ApiResponse.ok(outboxService.write(eventId, topic, aggregateId,
                aggregateVersion, payloadJson, schemaVersion));
    }

    @GetMapping("/outbox/pending")
    public ApiResponse<List<OutboxRow>> fetchPendingOutbox(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(outboxService.fetchPending(limit));
    }

    @PostMapping("/outbox/{id}/published")
    public ApiResponse<Void> markOutboxPublished(@PathVariable long id) {
        outboxService.markPublished(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/outbox/{id}/failed")
    public ApiResponse<String> markOutboxFailed(
            @PathVariable long id, @RequestParam(required = false) String error) {
        return ApiResponse.ok(outboxService.markFailed(id, error));
    }

    @PostMapping("/outbox/{id}/requeue")
    public ApiResponse<Void> requeueOutbox(@PathVariable long id) {
        outboxService.requeue(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/outbox/requeue-all")
    public ApiResponse<Integer> requeueAllFailedOutbox() {
        return ApiResponse.ok(outboxService.requeueAllFailed());
    }

    // ===== Inbox =====

    @PostMapping("/inbox/consume")
    public ApiResponse<Boolean> tryConsumeInbox(
            @RequestParam String consumerName,
            @RequestParam long eventId,
            @RequestParam String requestHash) {
        return ApiResponse.ok(inboxService.tryConsume(consumerName, eventId, requestHash));
    }

    @PostMapping("/inbox/{consumer}/{eventId}/processed")
    public ApiResponse<Void> markInboxProcessed(
            @PathVariable String consumer, @PathVariable long eventId) {
        inboxService.markProcessed(consumer, eventId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/inbox/{consumer}/{eventId}/failed")
    public ApiResponse<Void> markInboxFailed(
            @PathVariable String consumer, @PathVariable long eventId) {
        inboxService.markFailed(consumer, eventId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/inbox/failed")
    public ApiResponse<List<InboxRow>> findFailedInbox(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(inboxService.findFailed(limit));
    }

    @PostMapping("/inbox/{consumer}/{eventId}/requeue")
    public ApiResponse<Void> requeueInbox(
            @PathVariable String consumer, @PathVariable long eventId) {
        inboxService.requeue(consumer, eventId);
        return ApiResponse.ok(null);
    }

    // ===== 补单 =====

    @PostMapping("/backfills")
    public ApiResponse<Long> createBackfill(
            @RequestParam String sourceType,
            @RequestParam String sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(defaultValue = "10") int maxAttempts) {
        return ApiResponse.ok(backfillService.createBackfill(
                BackfillSourceType.valueOf(sourceType), sourceId, businessDate, maxAttempts));
    }

    @GetMapping("/backfills/pending")
    public ApiResponse<List<BackfillRow>> fetchPendingBackfills(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(backfillService.fetchPending(limit));
    }

    @PostMapping("/backfills/{id}/succeeded")
    public ApiResponse<Void> markBackfillSucceeded(@PathVariable long id) {
        backfillService.markSucceeded(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/backfills/{id}/failed")
    public ApiResponse<String> markBackfillFailed(
            @PathVariable long id, @RequestParam(required = false) String error) {
        return ApiResponse.ok(backfillService.markFailed(id, error));
    }

    @PostMapping("/backfills/{id}/reactivate")
    public ApiResponse<Void> reactivateBackfill(@PathVariable long id) {
        backfillService.reactivate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/backfills/from-failed-outbox")
    public ApiResponse<Integer> createBackfillsFromOutbox() {
        return ApiResponse.ok(backfillService.createBackfillsFromFailedOutbox());
    }

    @PostMapping("/backfills/from-failed-tasks")
    public ApiResponse<Integer> createBackfillsFromTasks() {
        return ApiResponse.ok(backfillService.createBackfillsFromFailedTasks());
    }

    // ===== 日终快照 =====

    @PostMapping("/snapshots/daily")
    public ApiResponse<Integer> snapshotAccounts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(snapshotService.snapshotAccountsForDay(businessDate));
    }

    @GetMapping("/snapshots/daily")
    public ApiResponse<SnapshotRow> getSnapshot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam long userId) {
        return ApiResponse.ok(snapshotService.getSnapshot(businessDate, userId));
    }

    @GetMapping("/snapshots/daily/verify")
    public ApiResponse<Boolean> verifySnapshot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam long userId) {
        return ApiResponse.ok(snapshotService.verifySnapshot(businessDate, userId));
    }

    // ===== CronJob =====

    @PostMapping("/tasks/start")
    public ApiResponse<TaskRunInfo> startTask(
            @RequestParam String taskType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam String owner) {
        return ApiResponse.ok(cronJobService.startTask(
                TaskType.valueOf(taskType), businessDate, owner));
    }

    @PostMapping("/tasks/{id}/cursor")
    public ApiResponse<Void> updateTaskCursor(
            @PathVariable long id,
            @RequestParam long cursor,
            @RequestParam long fencingToken) {
        cronJobService.updateCursor(id, cursor, fencingToken);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/{id}/finish")
    public ApiResponse<Void> finishTask(
            @PathVariable long id,
            @RequestParam String taskType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam String owner) {
        cronJobService.finishTask(id, TaskType.valueOf(taskType), businessDate, owner);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/{id}/fail")
    public ApiResponse<Void> failTask(
            @PathVariable long id,
            @RequestParam String taskType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam String owner,
            @RequestParam(required = false) String error) {
        cronJobService.failTask(id, TaskType.valueOf(taskType), businessDate, owner, error);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/renew-lease")
    public ApiResponse<Long> renewTaskLease(
            @RequestParam String taskType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam String owner) {
        return ApiResponse.ok(cronJobService.renewTaskLease(
                TaskType.valueOf(taskType), businessDate, owner));
    }

    @GetMapping("/tasks")
    public ApiResponse<TaskRunInfo> getTask(
            @RequestParam String taskType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.ok(cronJobService.getTaskRun(
                TaskType.valueOf(taskType), businessDate));
    }

    @GetMapping("/tasks/running")
    public ApiResponse<List<TaskRunInfo>> findRunningTasks() {
        return ApiResponse.ok(cronJobService.findRunningTasks());
    }
}
