package com.lianshengtong.release.feign;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * LSC账本服务 Feign 客户端
 * <p>
 * 调用 lsc-ledger-service 执行释放操作(L_LOCKED -> L_AVAILABLE)，
 * 由 Seata AT 模式保障跨服务一致性。
 * </p>
 */
@FeignClient(name = "lsc-ledger-service", contextId = "lscLedgerClient")
public interface LscLedgerFeignClient {

    /**
     * 执行单笔释放(锁定 -> 可用)
     *
     * @param opDTO 账本操作请求(transactionType=2 每日释放)
     * @return 操作结果
     */
    @PostMapping("/api/ledger/release")
    R<Object> release(@RequestBody LscLedgerOpDTO opDTO);

    /**
     * 批量释放(一批用户记录，每批默认10万条)
     *
     * @param batch 批量操作列表
     * @return 批量处理结果(成功条数等)
     */
    @PostMapping("/api/ledger/release/batch")
    R<Map<String, Object>> releaseBatch(@RequestBody List<LscLedgerOpDTO> batch);

    /**
     * 过期LSC批量转回(可用 -> 锁定)
     * <p>扫描全网过期可用明细，汇总后扣减可用、增加锁定。</p>
     *
     * @return 转回结果(转回用户数、转回总量)
     */
    @PostMapping("/api/ledger/expire-transfer-all")
    R<Map<String, Object>> expireTransfer();

    /**
     * 查询全网锁定余额汇总(每日释放任务加载待释放明细)
     *
     * @return Map: {totalLocked, userCount, accounts: List<{userId, totalLocked}>}
     */
    @GetMapping("/api/ledger/locked-summary")
    R<Map<String, Object>> lockedSummary();

    /**
     * 按日期+流水类型聚合统计(查询 N_total 前一日核销总额使用)
     *
     * @param date  目标日期(ISO yyyy-MM-dd)
     * @param types 逗号分隔的流水类型(可空)
     * @return Map: {totalAmount, totalCount}
     */
    @GetMapping("/api/ledger/daily-summary")
    R<Map<String, Object>> dailySummary(@RequestParam("date") String date,
                                         @RequestParam(value = "types", required = false) String types);
}
