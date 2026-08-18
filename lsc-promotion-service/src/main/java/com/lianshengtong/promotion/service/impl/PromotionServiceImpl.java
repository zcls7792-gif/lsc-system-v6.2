package com.lianshengtong.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.enums.OrderStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.promotion.dto.FirstOrderCheckDTO;
import com.lianshengtong.promotion.dto.RewardResultDTO;
import com.lianshengtong.promotion.dto.RollbackRewardDTO;
import com.lianshengtong.promotion.entity.PromotionPending;
import com.lianshengtong.promotion.feign.LedgerFeignClient;
import com.lianshengtong.promotion.feign.UserFeignClient;
import com.lianshengtong.promotion.mapper.PromotionPendingMapper;
import com.lianshengtong.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 推广服务实现
 * <p>
 * 严格一级推荐：奖励仅划转给直接推荐人(users.referrer_id)，禁止链式。
 * 并发安全：基于 Redisson 分布式锁保障同一用户首单判定与奖励划转串行化。
 * 一致性：奖励划转通过 Feign 调账本服务，Seata AT 保障跨服务一致性；
 * 划转失败落挂账表由定时任务补发。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionPendingMapper promotionPendingMapper;
    private final LedgerFeignClient ledgerFeignClient;
    private final UserFeignClient userFeignClient;
    private final RedissonClient redissonClient;

    /** 奖励比例：首单消费金额的 10% */
    @Value("${lsc.promotion.reward-rate:0.10}")
    private BigDecimal rewardRate;

    /** 首单门槛：有效消费 >= 1 元 */
    @Value("${lsc.promotion.first-order-min-amount:1}")
    private BigDecimal firstOrderMinAmount;

    /** 严格一级推荐 */
    @Value("${lsc.promotion.max-level:1}")
    private Integer maxLevel;

    /** 挂账补发批次大小 */
    @Value("${lsc.promotion.pending-batch-size:500}")
    private int pendingBatchSize;

    @Override
    public RewardResultDTO checkFirstOrder(FirstOrderCheckDTO dto) {
        // 1. 校验订单状态：需已完成且未全额退款
        boolean validOrder = OrderStatusEnum.COMPLETED.getCode() == dto.getOrderStatus()
                && (dto.getRefundAmount() == null
                    || dto.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                    || dto.getRefundAmount().compareTo(dto.getOrderAmount()) < 0);
        // 有效消费金额 >= 1 元
        BigDecimal validAmount = dto.getOrderAmount()
                .subtract(dto.getRefundAmount() == null ? BigDecimal.ZERO : dto.getRefundAmount());
        if (validAmount.compareTo(firstOrderMinAmount) < 0) {
            validOrder = false;
        }
        if (!validOrder) {
            return RewardResultDTO.builder().firstOrder(false).build();
        }

        // 2. Redisson 分布式锁防止并发首单判定
        String lockKey = "lsc:promotion:first-order:" + dto.getUserId();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BizException("首单判定并发，请稍后重试");
            }
            // 3. 查询是否已有该用户的首单奖励记录(已补发或挂账) -> 非首单
            //    严格一级：仅判定直接推荐关系，推荐人ID由调用方/上下文传递
            //    此处假设推荐人ID已通过 user-service 绑定，校验是否有挂账记录
            LambdaQueryWrapper<PromotionPending> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PromotionPending::getUserId, dto.getUserId())
                    .ne(PromotionPending::getStatus, 2);
            Long existCount = promotionPendingMapper.selectCount(wrapper);
            boolean isFirst = existCount == 0;
            return RewardResultDTO.builder()
                    .firstOrder(isFirst)
                    .firstOrderAmount(dto.getOrderAmount())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("首单判定被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RewardResultDTO calcReward(FirstOrderCheckDTO dto) {
        RewardResultDTO checkResult = checkFirstOrder(dto);
        if (Boolean.FALSE.equals(checkResult.getFirstOrder())) {
            return checkResult;
        }
        // 推荐人ID(严格一级，由 users.referrer_id 提供，此处从上下文获取)
        Long referrerId = dto.getReferrerId();
        if (referrerId == null) {
            log.warn("用户 {} 无推荐人，跳过奖励计算", dto.getUserId());
            return RewardResultDTO.builder().firstOrder(true).success(false).build();
        }
        BigDecimal rewardAmount = dto.getOrderAmount()
                .multiply(rewardRate)
                .setScale(2, RoundingMode.HALF_UP);

        // 尝试调用账本服务划转: 推荐人锁定池 -> 可用池
        LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                .idempotentKey("PROMOTION_" + dto.getUserId() + "_" + dto.getOrderNo())
                .transactionType(LscTransactionTypeEnum.PROMOTION_REWARD.getCode())
                .userId(referrerId)
                .lockedDelta(rewardAmount.negate().longValue())
                .availableDelta(rewardAmount.longValue())
                .orderNo(dto.getOrderNo())
                .remark("一级推广首单奖励 userId=" + dto.getUserId())
                .build();
        boolean transferSuccess = false;
        Long pendingId = null;
        try {
            R<Object> resp = ledgerFeignClient.ledgerOp(opDTO);
            transferSuccess = resp != null && resp.isSuccess();
        } catch (RuntimeException e) {
            log.error("奖励划转调用账本服务异常 userId={} referrer={}", dto.getUserId(), referrerId, e);
        }
        if (!transferSuccess) {
            // 划转失败 -> 落挂账表
            PromotionPending pending = new PromotionPending();
            pending.setUserId(dto.getUserId());
            pending.setReferrerId(referrerId);
            pending.setOrderNo(dto.getOrderNo());
            pending.setFirstOrderAmount(dto.getOrderAmount());
            pending.setRewardAmount(rewardAmount);
            pending.setStatus(0);
            pending.setRetryCount(0);
            pending.setRemark("账本服务划转失败，待补发");
            promotionPendingMapper.insert(pending);
            pendingId = pending.getId();
            log.warn("奖励划转失败已挂账 pendingId={} userId={}", pendingId, dto.getUserId());
        }
        return RewardResultDTO.builder()
                .firstOrder(true)
                .referrerId(referrerId)
                .firstOrderAmount(dto.getOrderAmount())
                .rewardAmount(rewardAmount)
                .success(transferSuccess)
                .pendingId(pendingId)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackReward(RollbackRewardDTO dto) {
        LambdaQueryWrapper<PromotionPending> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromotionPending::getUserId, dto.getUserId())
                .eq(PromotionPending::getOrderNo, dto.getOrderNo());
        PromotionPending pending = promotionPendingMapper.selectOne(wrapper);
        if (pending == null) {
            log.warn("回滚未找到挂账记录 userId={} orderNo={}", dto.getUserId(), dto.getOrderNo());
            return;
        }
        if (pending.getStatus() == 0) {
            // 仍挂账未补发 -> 直接置为废弃
            pending.setStatus(2);
            pending.setRemark("首单全额退款，挂账废弃");
            promotionPendingMapper.updateById(pending);
            log.info("挂账记录废弃 pendingId={}", pending.getId());
            return;
        }
        if (pending.getStatus() == 1) {
            // 已补发 -> 反向划转扣回奖励(推荐人可用池 -> 锁定池)
            LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                    .idempotentKey("PROMOTION_RB_" + dto.getUserId() + "_" + dto.getOrderNo())
                    .transactionType(LscTransactionTypeEnum.PROMOTION_REWARD.getCode())
                    .userId(pending.getReferrerId())
                    .lockedDelta(pending.getRewardAmount().longValue())
                    .availableDelta(pending.getRewardAmount().negate().longValue())
                    .orderNo(dto.getOrderNo())
                    .remark("首单全额退款奖励回滚 userId=" + dto.getUserId())
                    .build();
            R<Object> resp = ledgerFeignClient.ledgerOp(opDTO);
            if (resp == null || !resp.isSuccess()) {
                throw new BizException("奖励回滚划转失败，需人工处理");
            }
            pending.setStatus(2);
            pending.setRemark("首单全额退款，奖励已回滚");
            promotionPendingMapper.updateById(pending);
            log.info("奖励已回滚 pendingId={} referrer={}", pending.getId(), pending.getReferrerId());
        }
    }

    /**
     * 每日定时扫描挂账表自动补发
     */
    @Override
    @Scheduled(cron = "${lsc.promotion.pending-scan-cron:0 0 2 * * ?}")
    public int pendingAutoFill() {
        LambdaQueryWrapper<PromotionPending> wrapper = new LambdaQueryWrapper<>();
        // [SQL-fix] 边界校验，防止配置异常导致 LIMIT 负值或超大值
        int safeLimit = Math.max(1, Math.min(pendingBatchSize, 10000));
        wrapper.eq(PromotionPending::getStatus, 0)
                .last("LIMIT " + safeLimit);
        List<PromotionPending> pendings = promotionPendingMapper.selectList(wrapper);
        int success = 0;
        for (PromotionPending pending : pendings) {
            try {
                LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                        .idempotentKey("PROMOTION_FILL_" + pending.getId())
                        .transactionType(LscTransactionTypeEnum.PROMOTION_REWARD.getCode())
                        .userId(pending.getReferrerId())
                        .lockedDelta(pending.getRewardAmount().negate().longValue())
                        .availableDelta(pending.getRewardAmount().longValue())
                        .orderNo(pending.getOrderNo())
                        .remark("挂账补发 pendingId=" + pending.getId())
                        .build();
                R<Object> resp = ledgerFeignClient.ledgerOp(opDTO);
                if (resp != null && resp.isSuccess()) {
                    pending.setStatus(1);
                    pending.setRemark("挂账补发成功");
                    promotionPendingMapper.updateById(pending);
                    success++;
                } else {
                    pending.setRetryCount(pending.getRetryCount() + 1);
                    promotionPendingMapper.updateById(pending);
                }
            } catch (RuntimeException e) {
                log.error("挂账补发异常 pendingId={}", pending.getId(), e);
                pending.setRetryCount(pending.getRetryCount() + 1);
                pending.setRemark("补发异常: " + e.getMessage());
                promotionPendingMapper.updateById(pending);
            }
        }
        log.info("挂账补发扫描完成 总计{}条 成功{}条", pendings.size(), success);
        return success;
    }

    @Override
    public IPage<PromotionPending> pendingList(Integer page, Integer size, Integer status) {
        Page<PromotionPending> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<PromotionPending> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(PromotionPending::getStatus, status);
        }
        wrapper.orderByDesc(PromotionPending::getCreatedAt);
        return promotionPendingMapper.selectPage(p, wrapper);
    }

    @Override
    public void notifyFirstOrder(Long consumerId, String orderNo, BigDecimal orderAmount,
                                  Integer orderStatus, BigDecimal refundAmount) {
        if (consumerId == null || orderNo == null || orderAmount == null || orderStatus == null) {
            log.warn("首单通知参数缺失 consumerId={} orderNo={}", consumerId, orderNo);
            return;
        }
        // 1. 反查消费者推荐人(严格一级，仅 referrerId 单一字段)
        Long referrerId = null;
        try {
            R<java.util.Map<String, Object>> userR = userFeignClient.getUserInfo(consumerId);
            if (userR != null && userR.isSuccess() && userR.getData() != null) {
                Object ref = userR.getData().get("referrerId");
                if (ref != null) {
                    referrerId = Long.valueOf(String.valueOf(ref));
                }
            }
        } catch (RuntimeException e) {
            log.warn("查询用户推荐人失败 consumerId={} err={}", consumerId, e.getMessage());
        }

        // 2. 构造首单判定请求并触发奖励计算划转
        FirstOrderCheckDTO dto = new FirstOrderCheckDTO();
        dto.setUserId(consumerId);
        dto.setReferrerId(referrerId);
        dto.setOrderNo(orderNo);
        dto.setOrderAmount(orderAmount);
        dto.setOrderStatus(orderStatus);
        dto.setRefundAmount(refundAmount == null ? BigDecimal.ZERO : refundAmount);
        try {
            RewardResultDTO result = calcReward(dto);
            log.info("首单通知处理完成 consumerId={} orderNo={} firstOrder={} success={} pendingId={}",
                    consumerId, orderNo, result.getFirstOrder(), result.getSuccess(), result.getPendingId());
        } catch (RuntimeException e) {
            // 不抛出，避免阻断调用方主流程(order-service 已 try-catch 兜底)
            log.error("首单通知处理异常 consumerId={} orderNo={}", consumerId, orderNo, e);
        }
    }
}
