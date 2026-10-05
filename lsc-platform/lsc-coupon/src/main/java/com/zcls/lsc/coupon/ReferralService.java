package com.zcls.lsc.coupon;

import com.zcls.lsc.common.constants.LscConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 第8.1 / 8.2 / 8.5 章 推荐服务。
 *
 *  - 注册时绑定一名直接推荐人，绑定后不允许修改；禁止自推荐、推荐环路
 *  - 仅一级直推，无入门费、团队奖励或多级分佣
 *  - 首单（最早 COMPLETED）触发一次奖励；后来退款不重置触发资格
 *  - 发券事务锁推荐人计数行，success_sequence = counter+1，按序号选档位
 *  - 计数只增不减，退款后序号不复用
 *  - 源首单全退时撤销尚未使用的奖励券；已核销券不追收现金或 LSC 待追偿
 */
@Service
public class ReferralService {

    private final JdbcTemplate jdbc;
    private final CouponService couponService;

    public ReferralService(JdbcTemplate jdbc, CouponService couponService) {
        this.jdbc = jdbc;
        this.couponService = couponService;
    }

    /**
     * 绑定推荐关系（注册时）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindReferral(long referredUserId, long referrerUserId) {
        if (referredUserId == referrerUserId) {
            throw new IllegalArgumentException("self referral not allowed");
        }
        // 检查环路：推荐人不能是被推荐人的下游
        checkNoLoop(referredUserId, referrerUserId);

        jdbc.update(
                "INSERT INTO referral(referral_id, referred_user_id, referrer_user_id, bound_at, trigger_status) "
                        + "VALUES(?,?,?,?,?)",
                nextId(), referredUserId, referrerUserId,
                java.sql.Timestamp.valueOf(LocalDateTime.now()), "PENDING");
        jdbc.update("UPDATE user SET referrer_user_id=? WHERE user_id=?", referrerUserId, referredUserId);
    }

    /**
     * 订单完成时触发推荐奖励（若为首单）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void triggerRewardIfFirstOrder(long userId, long orderId) {
        // 查推荐关系
        Long referralId = jdbc.queryForObject(
                "SELECT referral_id FROM referral WHERE referred_user_id=? AND trigger_status='PENDING'",
                Long.class, userId);
        if (referralId == null) return;

        // 固化首单（服务端提交顺序，不按消息到达顺序）
        int updated = jdbc.update(
                "UPDATE referral SET first_order_id=?, trigger_status='TRIGGERED' "
                        + "WHERE referral_id=? AND trigger_status='PENDING'",
                orderId, referralId);
        if (updated == 0) return; // 已被其他事务触发

        // 查推荐人
        long referrerId = jdbc.queryForObject(
                "SELECT referrer_user_id FROM referral WHERE referral_id=?", Long.class, referralId);

        // 锁计数行，分配 success_sequence
        jdbc.queryForObject(
                "SELECT last_success_sequence FROM referral_counter WHERE referrer_user_id=? FOR UPDATE",
                Long.class, referrerId);
        // 若不存在则创建
        jdbc.update("INSERT IGNORE INTO referral_counter(referrer_user_id, last_success_sequence, version) "
                + "VALUES(?, 0, 0)", referrerId);
        Long seq = jdbc.queryForObject(
                "SELECT last_success_sequence FROM referral_counter WHERE referrer_user_id=? FOR UPDATE",
                Long.class, referrerId);
        long newSeq = seq + 1;
        jdbc.update("UPDATE referral_counter SET last_success_sequence=?, version=version+1 "
                + "WHERE referrer_user_id=?", newSeq, referrerId);

        // 选档位（第4位及以后同第4档）
        int tier = newSeq >= 4 ? 4 : (int) newSeq;
        long[] tierConfig = LscConstants.REFERRAL_TIERS[tier - 1];
        long faceCent = tierConfig[1];
        long minSpendCent = tierConfig[2];
        long validDays = tierConfig[3];

        // 创建奖励券（属性固化）
        long couponId = couponService.issueCoupon(referrerId, 0L, tier, null);

        // 写推荐奖励
        jdbc.update(
                "INSERT INTO referral_reward(reward_id, referral_id, referrer_user_id, success_sequence, "
                        + "reward_tier, first_order_id, coupon_id, status, source_refund_status, issued_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?)",
                nextId(), referralId, referrerId, newSeq, tier, orderId, couponId,
                "ISSUED", "NONE", java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * 第8.5章 源首单全退时撤销尚未使用的奖励券。
     * 已核销的券不向推荐人追收现金或增加 LSC 待追偿。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeRewardOnSourceRefund(long orderId) {
        var rewards = jdbc.queryForList(
                "SELECT reward_id, coupon_id FROM referral_reward WHERE first_order_id=?", orderId);

        for (var r : rewards) {
            long rewardId = ((Number) r.get("reward_id")).longValue();
            long couponId = ((Number) r.get("coupon_id")).longValue();

            String couponStatus = jdbc.queryForObject(
                    "SELECT status FROM user_coupon WHERE coupon_id=?", String.class, couponId);

            if ("AVAILABLE".equals(couponStatus) || "RESERVED".equals(couponStatus)) {
                // 若 RESERVED，先关闭未付使用订单并确认未付款后撤销
                if ("RESERVED".equals(couponStatus)) {
                    jdbc.update("UPDATE user_coupon SET status='AVAILABLE', reserved_order_id=NULL "
                            + "WHERE coupon_id=?", couponId);
                }
                jdbc.update("UPDATE user_coupon SET status='REVOKED' WHERE coupon_id=?", couponId);
                jdbc.update("UPDATE referral_reward SET status='REVOKED' WHERE reward_id=?", rewardId);
            } else if ("REDEEMED".equals(couponStatus)) {
                // 已核销：不追收现金或 LSC，仅记录营销损耗
                jdbc.update("UPDATE referral_reward SET status='USED' WHERE reward_id=?", rewardId);
            }
        }
    }

    /**
     * 检查推荐环路：从 referrer 向上追溯，若最终指向 referred 则存在环路。
     */
    private void checkNoLoop(long referredUserId, long referrerUserId) {
        long current = referrerUserId;
        int depth = 0;
        while (current > 0 && depth < 100) {
            if (current == referredUserId) {
                throw new IllegalArgumentException("referral loop detected");
            }
            Long next = jdbc.queryForObject(
                    "SELECT referrer_user_id FROM referral WHERE referred_user_id=?",
                    Long.class, current);
            if (next == null) break;
            current = next;
            depth++;
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
