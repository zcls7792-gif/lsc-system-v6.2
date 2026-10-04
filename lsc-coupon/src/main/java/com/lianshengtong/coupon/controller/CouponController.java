package com.lianshengtong.coupon.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.result.R;
import com.lianshengtong.coupon.entity.UserCoupon;
import com.lianshengtong.coupon.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 优惠券接口（V7.7.2 第八章）
 */
@RestController
@RequestMapping("/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final UserCouponMapper couponMapper;

    /** 用户券列表（按状态筛选） */
    @GetMapping
    public R<IPage<UserCoupon>> list(@RequestAttribute("userId") Long userId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Page<UserCoupon> p = new Page<>(page, size);
        LambdaQueryWrapper<UserCoupon> w = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getReceivedAt);
        if (status != null && !status.isEmpty()) {
            w.eq(UserCoupon::getStatus, status);
        }
        return R.ok(couponMapper.selectPage(p, w));
    }

    /** 券详情（含快照属性） */
    @GetMapping("/{couponId}")
    public R<UserCoupon> detail(@RequestAttribute("userId") Long userId,
                                 @PathVariable Long couponId) {
        UserCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            return R.fail(404, "券不存在");
        }
        return R.ok(coupon);
    }

    /** 检查券是否可用（下单前） */
    @GetMapping("/{couponId}/check")
    public R<Boolean> checkAvailable(@RequestAttribute("userId") Long userId,
                                      @PathVariable Long couponId,
                                      @RequestParam Long goodsCent) {
        UserCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            return R.fail(404, "券不存在");
        }
        if (!"AVAILABLE".equals(coupon.getStatus())) {
            return R.ok(false);
        }
        if (coupon.getValidUntil().isBefore(LocalDateTime.now())) {
            return R.ok(false);
        }
        if (goodsCent < coupon.getMinSpendCentSnapshot()) {
            return R.ok(false);
        }
        return R.ok(true);
    }
}
