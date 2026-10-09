package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第13.1章 优惠券接口。
 * GET /v1/coupons — 状态游标，返回快照面额、门槛、范围、截止。
 */
@RestController
@RequestMapping("/v1/coupons")
public class CouponController {

    private final JdbcTemplate jdbc;

    public CouponController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listCoupons(
            @RequestAttribute("userId") long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT coupon_id, face_cent_snapshot, min_spend_cent_snapshot, scope_snapshot_json, "
                        + "received_at, valid_until, status FROM user_coupon WHERE user_id=?");
        if (status != null) sql.append(" AND status='").append(status).append("'");
        if (cursor != null) sql.append(" AND coupon_id < ").append(cursor);
        sql.append(" ORDER BY coupon_id DESC LIMIT ?");

        List<Map<String, Object>> coupons = jdbc.queryForList(sql.toString(), userId, limit);
        return ApiResponse.ok(coupons);
    }
}
