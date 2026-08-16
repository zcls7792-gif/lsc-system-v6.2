package com.lianshengtong.reconciliation.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

/**
 * LSC 账本服务 Feign 客户端 - 对账聚合查询
 */
@FeignClient(name = "lsc-ledger-service", contextId = "reconciliationLedgerClient")
public interface LscLedgerFeignClient {

    /**
     * 按日期 + 流水类型聚合统计
     *
     * @param date  目标日期(ISO yyyy-MM-dd)
     * @param types 逗号分隔的流水类型(可空)
     * @return Map: {totalAmount, totalCount}
     */
    @GetMapping("/api/ledger/daily-summary")
    R<Map<String, Object>> dailySummary(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam(value = "types", required = false) String types);
}
