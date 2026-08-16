package com.lianshengtong.b2b.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.b2b.dto.B2bOrderCancelDTO;
import com.lianshengtong.b2b.dto.B2bOrderConfirmDTO;
import com.lianshengtong.b2b.dto.B2bOrderCreateDTO;
import com.lianshengtong.b2b.dto.B2bOrderTransferDTO;
import com.lianshengtong.b2b.dto.B2bOrderVoidDTO;
import com.lianshengtong.b2b.entity.B2bOrder;
import com.lianshengtong.b2b.feign.AiGatewayFeignClient;
import com.lianshengtong.b2b.feign.LscLedgerFeignClient;
import com.lianshengtong.b2b.mapper.B2bOrderMapper;
import com.lianshengtong.b2b.service.B2bOrderService;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.B2BOrderStatusEnum;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.IdempotentKeyGenerator;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.ShardedLockUtil;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * B2B 交易订单服务实现
 * <p>
 * 订单状态机：待确认(0) -&gt; 已确认(1) -&gt; 已流转(2) -&gt; 已完成(3)
 *           待确认(0)/已确认(1) -&gt; 已取消(4)
 *           任意状态 -&gt; 已作废(5)(风控/AI判定)
 * 流转执行通过 Feign 调用 lsc-ledger-service 完成原子化账务操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class B2bOrderServiceImpl implements B2bOrderService {

    private final B2bOrderMapper b2bOrderMapper;
    private final LscLedgerFeignClient lscLedgerFeignClient;
    private final AiGatewayFeignClient aiGatewayFeignClient;
    private final RedissonClient redissonClient;
    private final ShardedLockUtil shardedLockUtil;

    @Value("${lsc.b2b.order-validity-days:7}")
    private int orderValidityDays;

    @Value("${lsc.b2b.lock-wait-ms:3000}")
    private long lockWaitMs;

    @Value("${lsc.b2b.lock-lease-ms:10000}")
    private long lockLeaseMs;

    private static final String LOCK_PREFIX = "lock:b2b:order:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public B2bOrder createOrder(B2bOrderCreateDTO dto) {
        // 校验 1:1 比例：LSC 数量须与人民币总金额一致
        if (dto.getLscAmount().compareTo(dto.getTotalAmountRmb().longValue()) != 0) {
            throw new BizException(ResultCode.B2B_AMOUNT_MISMATCH);
        }
        if (dto.getInitiatorId().equals(dto.getCounterpartyId())) {
            throw new BizException("发起方与接收方不能为同一商家");
        }

        B2bOrder order = new B2bOrder();
        long snowflake = SnowflakeIdUtil.id();
        order.setId(snowflake);
        order.setOrderNo(generateOrderNo(snowflake));
        order.setInitiatorId(dto.getInitiatorId());
        order.setCounterpartyId(dto.getCounterpartyId());
        order.setTradeDescription(dto.getTradeDescription());
        order.setTotalAmountRmb(dto.getTotalAmountRmb());
        order.setLscAmount(dto.getLscAmount());
        order.setContractNo(dto.getContractNo());
        order.setTradeEvidenceUrls(dto.getTradeEvidenceUrls());
        // 初始核验未核验、未确认、未流转
        order.setAiVerificationResult(0);
        order.setCounterpartyConfirmed(0);
        order.setLscTransferred(0);
        order.setStatus(B2BOrderStatusEnum.PENDING_CONFIRM.getCode());
        order.setVersion(1);
        order.setExpireAt(LocalDateTime.now().plusDays(orderValidityDays));
        order.setIdempotentKey(IdempotentKeyGenerator.generate("B2B_CREATE", dto.getInitiatorId()));

        b2bOrderMapper.insert(order);
        log.info("B2B订单创建成功 orderNo={} initiatorId={} counterpartyId={} lscAmount={}",
                order.getOrderNo(), order.getInitiatorId(), order.getCounterpartyId(), order.getLscAmount());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public B2bOrder confirmOrder(B2bOrderConfirmDTO dto) {
        B2bOrder order = getByOrderNo(dto.getOrderNo());
        // 校验身份：确认人必须是订单接收方
        if (!order.getCounterpartyId().equals(dto.getConfirmerId())) {
            throw new BizException(ResultCode.FORBIDDEN, "仅接收方可确认订单");
        }
        if (order.getStatus() != B2BOrderStatusEnum.PENDING_CONFIRM.getCode()) {
            throw new BizException("订单当前状态不可确认");
        }

        RLock lock;
        try {
            lock = shardedLockUtil.tryShardedLock(LOCK_PREFIX, order.getOrderNo(), lockWaitMs, lockLeaseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单确认被中断");
        }
        if (lock == null) {
            throw new BizException("订单确认处理中，请稍后重试");
        }
        try {
            order.setCounterpartyConfirmed(1);
            order.setConfirmedBy(StrUtil.blankToDefault(dto.getConfirmedBy(), String.valueOf(dto.getConfirmerId())));
            order.setConfirmedAt(LocalDateTime.now());
            order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());
            // updateById 触发乐观锁，version 不匹配返回 0
            int rows = b2bOrderMapper.updateById(order);
            if (rows == 0) {
                throw new BizException("订单确认失败，订单状态已变更，请重试");
            }
            log.info("B2B订单确认成功 orderNo={} confirmerId={}", order.getOrderNo(), dto.getConfirmerId());
            return order;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public B2bOrder executeTransfer(B2bOrderTransferDTO dto) {
        B2bOrder order = getByOrderNo(dto.getOrderNo());
        // 仅发起方可执行流转
        if (!order.getInitiatorId().equals(dto.getOperatorId())) {
            throw new BizException(ResultCode.FORBIDDEN, "仅发起方可执行流转");
        }
        if (order.getStatus() != B2BOrderStatusEnum.CONFIRMED.getCode()) {
            throw new BizException(ResultCode.B2B_NOT_CONFIRMED);
        }
        if (order.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException("订单已过期，不可流转");
        }
        // 1:1 校验：LSC 数量须等于人民币总金额
        if (order.getLscAmount().compareTo(order.getTotalAmountRmb().longValue()) != 0) {
            throw new BizException(ResultCode.B2B_AMOUNT_MISMATCH);
        }

        RLock lock;
        try {
            lock = shardedLockUtil.tryShardedLock(LOCK_PREFIX, order.getOrderNo(), lockWaitMs, lockLeaseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单流转被中断");
        }
        if (lock == null) {
            throw new BizException("订单流转处理中，请稍后重试");
        }
        try {
            // 调用账本服务执行 1:1 流转
            LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                    .idempotentKey(IdempotentKeyGenerator.generate("B2B_TRANSFER", order.getInitiatorId()))
                    .transactionType(LscTransactionTypeEnum.B2B_TRANSFER.getCode())
                    .userId(order.getInitiatorId())
                    .counterpartyId(order.getCounterpartyId())
                    .availableDelta(-order.getLscAmount())
                    .orderNo(order.getOrderNo())
                    .remark("B2B 1:1 流转")
                    .build();
            R<Void> result = lscLedgerFeignClient.b2bTransfer(opDTO);
            if (result == null || !result.isSuccess()) {
                throw new BizException(ResultCode.SEATA_TRANSACTION_EXCEPTION,
                        "账本流转失败: " + (result == null ? "服务无响应" : result.getMessage()));
            }
            // 流转成功，更新订单状态
            order.setLscTransferred(1);
            order.setStatus(B2BOrderStatusEnum.TRANSFERRED.getCode());
            order.setCompletedAt(LocalDateTime.now());
            int rows = b2bOrderMapper.updateById(order);
            if (rows == 0) {
                throw new BizException("订单状态更新失败(乐观锁冲突)，请核对账本后人工处理");
            }
            log.info("B2B订单流转成功 orderNo={} from={} to={} amount={}",
                    order.getOrderNo(), order.getInitiatorId(), order.getCounterpartyId(), order.getLscAmount());
            return order;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public B2bOrder cancelOrder(B2bOrderCancelDTO dto) {
        B2bOrder order = getByOrderNo(dto.getOrderNo());
        // 双方均可取消
        boolean isInitiator = order.getInitiatorId().equals(dto.getOperatorId());
        boolean isCounterparty = order.getCounterpartyId().equals(dto.getOperatorId());
        if (!isInitiator && !isCounterparty) {
            throw new BizException(ResultCode.FORBIDDEN, "非订单参与方，无权取消");
        }
        int status = order.getStatus();
        if (status != B2BOrderStatusEnum.PENDING_CONFIRM.getCode()
                && status != B2BOrderStatusEnum.CONFIRMED.getCode()) {
            throw new BizException("订单当前状态不可取消");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + order.getOrderNo());
        try {
            if (!lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS)) {
                throw new BizException("订单取消处理中，请稍后重试");
            }
            order.setStatus(B2BOrderStatusEnum.CANCELLED.getCode());
            int rows = b2bOrderMapper.updateById(order);
            if (rows == 0) {
                throw new BizException("订单取消失败，订单状态已变更，请重试");
            }
            log.info("B2B订单取消成功 orderNo={} operatorId={}", order.getOrderNo(), dto.getOperatorId());
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单取消被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public B2bOrder voidOrder(B2bOrderVoidDTO dto) {
        B2bOrder order = getByOrderNo(dto.getOrderNo());
        // 已流转/已完成的订单不可作废(账务已发生)
        int status = order.getStatus();
        if (status == B2BOrderStatusEnum.TRANSFERRED.getCode()
                || status == B2BOrderStatusEnum.COMPLETED.getCode()) {
            throw new BizException("订单已流转/完成，不可作废，请走对账流程");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + order.getOrderNo());
        try {
            if (!lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS)) {
                throw new BizException("订单作废处理中，请稍后重试");
            }
            order.setStatus(B2BOrderStatusEnum.VOIDED.getCode());
            // 作废后冻结流转权限：lscTransferred 置 0 并通过状态机阻止流转
            int rows = b2bOrderMapper.updateById(order);
            if (rows == 0) {
                throw new BizException("订单作废失败，订单状态已变更，请重试");
            }
            log.warn("B2B订单作废 orderNo={} operatorId={} reason={}", order.getOrderNo(), dto.getOperatorId(), dto.getReason());
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单作废被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Map<String, Object> getAiVerification(String orderNo) {
        B2bOrder order = getByOrderNo(orderNo);
        R<Map<String, Object>> result = aiGatewayFeignClient.b2bVerify(
                order.getOrderNo(), order.getTradeDescription(), order.getTradeEvidenceUrls());
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BizException("AI核验服务调用失败: " + (result == null ? "无响应" : result.getMessage()));
        }
        Map<String, Object> data = result.getData();
        // 回写订单核验字段
        B2bOrder update = new B2bOrder();
        update.setId(order.getId());
        update.setVersion(order.getVersion());
        Object resultVal = data.get("result");
        Object scoreVal = data.get("score");
        if (resultVal != null) {
            update.setAiVerificationResult(Integer.parseInt(String.valueOf(resultVal)));
        }
        if (scoreVal != null) {
            update.setAiVerificationScore(new BigDecimal(String.valueOf(scoreVal)));
        }
        Object riskTags = data.get("riskTags");
        if (riskTags != null) {
            update.setAiRiskTags(String.valueOf(riskTags));
        }
        b2bOrderMapper.updateById(update);
        log.info("B2B订单AI核验完成 orderNo={} result={} score={}",
                orderNo, resultVal, scoreVal);
        return data;
    }

    @Override
    public B2bOrder getByOrderNo(String orderNo) {
        B2bOrder order = b2bOrderMapper.selectOne(
                new LambdaQueryWrapper<B2bOrder>().eq(B2bOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.B2B_ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    public IPage<B2bOrder> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status) {
        return listOrders(pageNum, pageSize, userId, status, null, null, null);
    }

    @Override
    public B2bOrder manualVerifyConfirm(String orderNo, Boolean pass, String remark) {
        B2bOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(404, "B2B订单不存在: " + orderNo);
        }
        // 0未核验 1AI真实 2AI可疑 3人工真实 4人工虚假
        int target = Boolean.TRUE.equals(pass) ? 3 : 4;
        order.setAiVerificationResult(target);
        if (StrUtil.isNotBlank(remark)) {
            String existingTags = order.getAiRiskTags();
            order.setAiRiskTags(StrUtil.isBlank(existingTags) ? "人工:" + remark : existingTags + ";人工:" + remark);
        }
        // 人工判定虚假则作废订单
        if (Boolean.FALSE.equals(pass)) {
            order.setStatus(B2BOrderStatusEnum.VOIDED.getCode());
        }
        b2bOrderMapper.updateById(order);
        log.info("B2B订单人工核验 orderNo={} pass={} remark={}", orderNo, pass, remark);
        return order;
    }

    @Override
    public IPage<B2bOrder> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status,
                                      String orderNo, String startDate, String endDate) {
        Page<B2bOrder> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<B2bOrder> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.and(w -> w.eq(B2bOrder::getInitiatorId, userId)
                    .or().eq(B2bOrder::getCounterpartyId, userId));
        }
        if (status != null) {
            wrapper.eq(B2bOrder::getStatus, status);
        }
        if (StrUtil.isNotBlank(orderNo)) {
            wrapper.like(B2bOrder::getOrderNo, orderNo);
        }
        if (StrUtil.isNotBlank(startDate)) {
            wrapper.ge(B2bOrder::getCreatedAt, java.time.LocalDate.parse(startDate).atStartOfDay());
        }
        if (StrUtil.isNotBlank(endDate)) {
            wrapper.le(B2bOrder::getCreatedAt, java.time.LocalDate.parse(endDate).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(B2bOrder::getCreatedAt);
        return b2bOrderMapper.selectPage(page, wrapper);
    }

    /**
     * 生成 B2B 订单号：B2B + yyyyMMddHHmmss + 雪花后6位
     */
    private String generateOrderNo(long snowflake) {
        return "B2B" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss")
                + String.format("%06d", Math.abs(snowflake % 1_000_000));
    }
}
