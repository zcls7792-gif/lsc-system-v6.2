package com.lianshengtong.coupon.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.coupon.entity.*;
import com.lianshengtong.coupon.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 推荐奖励服务（V7.7.2 第八章 8.1-8.2）
 * <p>
 * 4档推荐券：
 * 第1位：20元 满200 30天
 * 第2位：30元 满250 40天
 * 第3位：40元 满300 50天
 * 第4位及以后：40元 满300 60天
 * </p>
 * <p>
 * 并发控制：锁推荐人计数行，分配 success_sequence = counter + 1，
 * 唯一键 (referrer_user_id, success_sequence) 保证不重复。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralRewardService {

    private final ReferralCounterMapper counterMapper;
    private final ReferralRewardMapper rewardMapper;
    private final UserCouponMapper couponMapper;
    private final CouponTemplateVersionMapper templateMapper;

    /** 档位定义：successSequence -> [面额分, 门槛分, 有效天数] */
    private static final long[][] TIER_RULES = {
            {2000L, 20000L, 30},   // 第1位：20元 满200 30天
            {3000L, 25000L, 40},   // 第2位：30元 满250 40天
            {4000L, 30000L, 50},   // 第3位：40元 满300 50天
            {4000L, 30000L, 60}    // 第4位及以后：40元 满300 60天
    };

    /**
     * 触发推荐奖励（在首单完成事务内调用）。
     *
     * @param referralId       推荐关系ID
     * @param referrerUserId   推荐人
     * @param referredUserId   被推荐人
     * @param firstOrderId     首单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ReferralReward triggerReward(Long referralId, Long referrerUserId,
                                         Long referredUserId, Long firstOrderId) {
        // 幂等：检查是否已触发
        ReferralReward existing = rewardMapper.selectOne(
                new LambdaQueryWrapper<ReferralReward>()
                        .eq(ReferralReward::getReferralId, referralId));
        if (existing != null) return existing;

        // 锁推荐人计数行
        ReferralCounter counter = counterMapper.selectByIdForUpdate(referrerUserId);
        if (counter == null) {
            counter = new ReferralCounter();
            counter.setReferrerUserId(referrerUserId);
            counter.setLastSuccessSequence(0);
            counter.setVersion(0);
            counterMapper.insert(counter);
            counter = counterMapper.selectByIdForUpdate(referrerUserId);
        }

        int seq = counter.getLastSuccessSequence() + 1;
        int tier = Math.min(seq, 4);

        // 选择档位模板
        long[] rule = TIER_RULES[tier - 1];
        long faceCent = rule[0];
        long minSpendCent = rule[1];
        int validDays = (int) rule[2];

        // 创建用户券（固化属性）
        UserCoupon coupon = new UserCoupon();
        coupon.setCouponId(IdUtil.getSnowflakeNextId());
        coupon.setUserId(referrerUserId);
        coupon.setTemplateId(null);
        coupon.setTemplateVersion(1);
        coupon.setFaceCentSnapshot(faceCent);
        coupon.setMinSpendCentSnapshot(minSpendCent);
        coupon.setScopeSnapshotJson(null);
        LocalDateTime now = LocalDateTime.now();
        coupon.setReceivedAt(now);
        coupon.setValidUntil(now.plusDays(validDays));
        coupon.setStatus("AVAILABLE");
        coupon.setVersion(0);
        couponMapper.insert(coupon);

        // 创建奖励记录
        ReferralReward reward = new ReferralReward();
        reward.setRewardId(IdUtil.getSnowflakeNextId());
        reward.setReferralId(referralId);
        reward.setReferrerUserId(referrerUserId);
        reward.setSuccessSequence(seq);
        reward.setRewardTier(tier);
        reward.setFirstOrderId(firstOrderId);
        reward.setCouponId(coupon.getCouponId());
        reward.setStatus("ISSUED");
        reward.setSourceRefundStatus("NONE");
        reward.setIssuedAt(now);
        rewardMapper.insert(reward);

        coupon.setSourceRewardId(reward.getRewardId());
        couponMapper.updateById(coupon);

        // 更新计数
        counter.setLastSuccessSequence(seq);
        counterMapper.updateById(counter);

        return reward;
    }

    /**
     * 源首单全退时撤销奖励券（V7.7.2 第八章 8.5）。
     * 已核销的券不追收现金，仅记录营销损耗。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeRewardOnSourceRefund(Long referralId) {
        ReferralReward reward = rewardMapper.selectOne(
                new LambdaQueryWrapper<ReferralReward>()
                        .eq(ReferralReward::getReferralId, referralId));
        if (reward == null) return;

        UserCoupon coupon = couponMapper.selectById(reward.getCouponId());
        if (coupon == null) return;

        if ("AVAILABLE".equals(coupon.getStatus())) {
            coupon.setStatus("REVOKED");
            couponMapper.updateById(coupon);
            reward.setStatus("REVOKED");
        } else if ("RESERVED".equals(coupon.getStatus())) {
            // 已占用：先关闭未付订单再撤销（由订单服务处理），此处标记
            reward.setStatus("REVOKE_PENDING");
        }
        // REDEEMED：不追收，记录营销损耗
        reward.setSourceRefundStatus("FULL_REFUND");
        rewardMapper.updateById(reward);
    }
}
