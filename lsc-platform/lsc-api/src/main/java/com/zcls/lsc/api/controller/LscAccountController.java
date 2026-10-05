package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.common.money.Units;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第13.1章 权益账本接口。
 *
 *  - GET /v1/lsc/account — 各桶 unit 字符串及追偿说明
 *  - GET /v1/lsc/events — 游标，释放/返还/扣回/过期及实际到账
 */
@RestController
@RequestMapping("/v1/lsc")
public class LscAccountController {

    private final JdbcTemplate jdbc;

    public LscAccountController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询账户权益余额。
     * 个人中心展示锁定、可用、风险冻结余额，并单列"支付占用"。
     * unit 以字符串返回避免 JS 大整数损失。
     */
    @GetMapping("/account")
    public ApiResponse<Map<String, Object>> getAccount(@RequestAttribute("userId") long userId) {
        var row = jdbc.queryForMap(
                "SELECT locked_unit, available_unit, reserved_unit, frozen_locked_unit, "
                        + "frozen_available_unit, pending_recovery_unit FROM lsc_account WHERE user_id=?",
                userId);

        return ApiResponse.ok(Map.of(
                "lockedUnit", str(row.get("locked_unit")),
                "availableUnit", str(row.get("available_unit")),
                "reservedUnit", str(row.get("reserved_unit")),
                "frozenLockedUnit", str(row.get("frozen_locked_unit")),
                "frozenAvailableUnit", str(row.get("frozen_available_unit")),
                "pendingRecoveryUnit", str(row.get("pending_recovery_unit")),
                "displayLsc", Map.of(
                        "locked", Units.of(((Number) row.get("locked_unit")).longValue()).displayLsc(),
                        "available", Units.of(((Number) row.get("available_unit")).longValue()).displayLsc(),
                        "reserved", Units.of(((Number) row.get("reserved_unit")).longValue()).displayLsc()
                )
        ));
    }

    /**
     * 查询权益事件流水（按 user_event_seq 排序，游标分页）。
     */
    @GetMapping("/events")
    public ApiResponse<List<Map<String, Object>>> getEvents(
            @RequestAttribute("userId") long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        long seq = cursor == null ? Long.MAX_VALUE : cursor;
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT event_id, user_event_seq, event_type, business_date, occurred_at, rule_version "
                        + "FROM lsc_event WHERE user_id=? AND user_event_seq<? "
                        + "ORDER BY user_event_seq DESC LIMIT ?",
                userId, seq, limit);
        return ApiResponse.ok(events);
    }

    private String str(Object o) {
        return o == null ? "0" : o.toString();
    }
}
