package com.lianshengtong.order.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.enums.OrderStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.IdempotentKeyGenerator;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import com.lianshengtong.order.dto.OrderCreateDTO;
import com.lianshengtong.order.dto.OrderPayDTO;
import com.lianshengtong.order.dto.OrderRefundDTO;
import com.lianshengtong.order.entity.Order;
import com.lianshengtong.order.feign.LscLedgerFeignClient;
import com.lianshengtong.order.feign.PromotionFeignClient;
import com.lianshengtong.order.mapper.OrderMapper;
import com.lianshengtong.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现
 * <p>
 * 订单状态机：待支付(0) -&gt; 已支付(1) -&gt; 已完成(2)
 *           待支付(0) -&gt; 已取消(3)
 *           已支付(1)/已完成(2) -&gt; 已退款(4) / 部分退款(5)
 * 支付/退款通过 Feign 调用 lsc-ledger-service 完成 LSC 原子化账务操作；
 * 人民币支付/退款由支付机构处理(此处模拟)。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final LscLedgerFeignClient lscLedgerFeignClient;
    private final PromotionFeignClient promotionFeignClient;
    private final RedissonClient redissonClient;

    @Value("${lsc.order.lock-wait-ms:3000}")
    private long lockWaitMs;

    @Value("${lsc.order.lock-lease-ms:10000}")
    private long lockLeaseMs;

    private static final String LOCK_PREFIX = "lock:order:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderCreateDTO dto) {
        // 计算混合支付拆分：LSC 数量(1:1对应人民币元) + 人民币补足
        long totalLong = dto.getTotalPrice().longValue();
        Long lscAmount = dto.getLscAmount() == null ? 0L : dto.getLscAmount();
        if (lscAmount < 0) {
            throw new BizException("LSC支付数量不能为负");
        }
        if (lscAmount > totalLong) {
            throw new BizException("LSC支付数量不能超过订单总价");
        }
        BigDecimal rmbAmount = dto.getTotalPrice().subtract(BigDecimal.valueOf(lscAmount));

        Order order = new Order();
        long snowflake = SnowflakeIdUtil.id();
        order.setId(snowflake);
        order.setOrderNo(generateOrderNo(snowflake));
        order.setOrderType(dto.getOrderType());
        order.setConsumerId(dto.getConsumerId());
        order.setMerchantId(dto.getMerchantId());
        order.setProductId(dto.getProductId() == null ? 0L : dto.getProductId());
        order.setProductName(dto.getProductName());
        order.setQuantity(dto.getQuantity() == null ? 1 : dto.getQuantity());
        order.setTotalPrice(dto.getTotalPrice());
        order.setLscAmount(lscAmount);
        order.setRmbAmount(rmbAmount);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);

        orderMapper.insert(order);
        log.info("订单创建成功 orderNo={} consumerId={} merchantId={} totalPrice={} lsc={} rmb={}",
                order.getOrderNo(), order.getConsumerId(), order.getMerchantId(),
                order.getTotalPrice(), order.getLscAmount(), order.getRmbAmount());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order payOrder(OrderPayDTO dto) {
        Order order = getByOrderNo(dto.getOrderNo());
        if (!order.getConsumerId().equals(dto.getConsumerId())) {
            throw new BizException(ResultCode.FORBIDDEN, "仅下单消费者可支付");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_PAY.getCode()) {
            throw new BizException("订单当前状态不可支付");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + order.getOrderNo());
        try {
            if (!lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS)) {
                throw new BizException("订单支付处理中，请稍后重试");
            }
            // 1. LSC 部分支付：调用账本服务扣减消费者可用 LSC 并转入商家
            if (order.getLscAmount() > 0) {
                LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                        .idempotentKey(IdempotentKeyGenerator.generate("ORDER_PAY", order.getConsumerId()))
                        .transactionType(LscTransactionTypeEnum.MALL_CONSUMPTION.getCode())
                        .userId(order.getConsumerId())
                        .counterpartyId(order.getMerchantId())
                        .availableDelta(-order.getLscAmount())
                        .orderNo(order.getOrderNo())
                        .remark("订单LSC支付")
                        .build();
                R<Void> result = lscLedgerFeignClient.payLsc(opDTO);
                if (result == null || !result.isSuccess()) {
                    throw new BizException(ResultCode.SEATA_TRANSACTION_EXCEPTION,
                            "LSC支付失败: " + (result == null ? "账本服务无响应" : result.getMessage()));
                }
            }
            // 2. 人民币部分支付：唤起支付机构(此处模拟，实际调用支付网关)
            if (order.getRmbAmount().compareTo(BigDecimal.ZERO) > 0) {
                invokeRmbPayment(order.getOrderNo(), order.getRmbAmount());
            }
            order.setStatus(OrderStatusEnum.PAID.getCode());
            order.setPayTime(LocalDateTime.now());
            orderMapper.updateById(order);

            log.info("订单支付成功 orderNo={} lsc={} rmb={}",
                    order.getOrderNo(), order.getLscAmount(), order.getRmbAmount());
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单支付被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order completeOrder(String orderNo, Long operatorId) {
        Order order = getByOrderNo(orderNo);
        if (!order.getMerchantId().equals(operatorId)) {
            throw new BizException(ResultCode.FORBIDDEN, "仅订单商家可确认完成");
        }
        if (order.getStatus() != OrderStatusEnum.PAID.getCode()) {
            throw new BizException("订单当前状态不可完成");
        }
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 通知推广服务(首单奖励释放)；失败不阻断主流程，由对账任务补偿
        try {
            BigDecimal paidAmount = order.getTotalPrice() == null
                    ? BigDecimal.ZERO : order.getTotalPrice();
            BigDecimal refunded = order.getRefundRmbAmount() == null
                    ? BigDecimal.ZERO : order.getRefundRmbAmount();
            promotionFeignClient.notifyFirstOrder(
                    order.getConsumerId(), order.getOrderNo(),
                    paidAmount, OrderStatusEnum.COMPLETED.getCode(), refunded);
        } catch (RuntimeException e) {
            log.warn("通知推广首单失败 orderNo={} err={}", order.getOrderNo(), e.getMessage());
        }
        log.info("订单完成 orderNo={}", orderNo);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(String orderNo, Long operatorId) {
        Order order = getByOrderNo(orderNo);
        if (order.getStatus() != OrderStatusEnum.PENDING_PAY.getCode()) {
            throw new BizException("仅待支付订单可取消");
        }
        boolean isConsumer = order.getConsumerId().equals(operatorId);
        boolean isMerchant = order.getMerchantId().equals(operatorId);
        if (!isConsumer && !isMerchant) {
            throw new BizException(ResultCode.FORBIDDEN, "非订单参与方，无权取消");
        }
        order.setStatus(OrderStatusEnum.CANCELLED.getCode());
        orderMapper.updateById(order);
        log.info("订单取消 orderNo={} operatorId={}", orderNo, operatorId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order refundOrder(OrderRefundDTO dto) {
        Order order = getByOrderNo(dto.getOrderNo());
        if (!order.getMerchantId().equals(dto.getOperatorId())) {
            throw new BizException(ResultCode.FORBIDDEN, "仅订单商家可发起退款");
        }
        int status = order.getStatus();
        if (status != OrderStatusEnum.PAID.getCode() && status != OrderStatusEnum.COMPLETED.getCode()) {
            throw new BizException("订单当前状态不可退款");
        }
        // 校验是否已退过(全额退款要求未发生过退款)
        if (order.getRefundLscAmount() > 0 || order.getRefundRmbAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException("订单已发生退款，请走部分退款流程");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + order.getOrderNo());
        try {
            if (!lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS)) {
                throw new BizException("订单退款处理中，请稍后重试");
            }
            // 1. LSC 全额退回消费者(触发发行回滚)
            if (order.getLscAmount() > 0) {
                LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                        .idempotentKey(IdempotentKeyGenerator.generate("ORDER_REFUND", order.getMerchantId()))
                        .transactionType(LscTransactionTypeEnum.REFUND_RETURN.getCode())
                        .userId(order.getConsumerId())
                        .counterpartyId(order.getMerchantId())
                        .availableDelta(order.getLscAmount())
                        .orderNo(order.getOrderNo())
                        .remark("订单全额退款LSC退回")
                        .build();
                R<Void> result = lscLedgerFeignClient.refundLsc(opDTO);
                if (result == null || !result.isSuccess()) {
                    throw new BizException(ResultCode.REFUND_LSC_INSUFFICIENT,
                            "LSC退款失败: " + (result == null ? "账本服务无响应" : result.getMessage()));
                }
            }
            // 2. 人民币全额退回(此处模拟，实际调用支付机构退款)
            if (order.getRmbAmount().compareTo(BigDecimal.ZERO) > 0) {
                invokeRmbRefund(order.getOrderNo(), order.getRmbAmount());
            }
            order.setRefundLscAmount(order.getLscAmount());
            order.setRefundRmbAmount(order.getRmbAmount());
            order.setStatus(OrderStatusEnum.REFUNDED.getCode());
            orderMapper.updateById(order);
            log.info("订单全额退款完成 orderNo={} refundLsc={} refundRmb={}",
                    order.getOrderNo(), order.getRefundLscAmount(), order.getRefundRmbAmount());
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单退款被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order partialRefund(OrderRefundDTO dto) {
        Order order = getByOrderNo(dto.getOrderNo());
        if (!order.getMerchantId().equals(dto.getOperatorId())) {
            throw new BizException(ResultCode.FORBIDDEN, "仅订单商家可发起退款");
        }
        int status = order.getStatus();
        if (status != OrderStatusEnum.PAID.getCode()
                && status != OrderStatusEnum.COMPLETED.getCode()
                && status != OrderStatusEnum.PARTIAL_REFUNDED.getCode()) {
            throw new BizException("订单当前状态不可部分退款");
        }
        Long refundLsc = dto.getRefundLscAmount() == null ? 0L : dto.getRefundLscAmount();
        BigDecimal refundRmb = dto.getRefundRmbAmount() == null ? BigDecimal.ZERO : dto.getRefundRmbAmount();
        if (refundLsc <= 0 && refundRmb.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("本次退款金额必须大于0");
        }
        // 校验累计退款不超过实付
        long totalRefundLsc = order.getRefundLscAmount() + refundLsc;
        BigDecimal totalRefundRmb = order.getRefundRmbAmount().add(refundRmb);
        if (totalRefundLsc > order.getLscAmount()) {
            throw new BizException("累计退款LSC超过实付LSC");
        }
        if (totalRefundRmb.compareTo(order.getRmbAmount()) > 0) {
            throw new BizException("累计退款人民币超过实付人民币");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + order.getOrderNo());
        try {
            if (!lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS)) {
                throw new BizException("订单退款处理中，请稍后重试");
            }
            // 1. 部分退回 LSC
            if (refundLsc > 0) {
                LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                        .idempotentKey(IdempotentKeyGenerator.generate("ORDER_PARTIAL_REFUND", order.getMerchantId()))
                        .transactionType(LscTransactionTypeEnum.REFUND_RETURN.getCode())
                        .userId(order.getConsumerId())
                        .counterpartyId(order.getMerchantId())
                        .availableDelta(refundLsc)
                        .orderNo(order.getOrderNo())
                        .remark("订单部分退款LSC退回")
                        .build();
                R<Void> result = lscLedgerFeignClient.refundLsc(opDTO);
                if (result == null || !result.isSuccess()) {
                    throw new BizException(ResultCode.REFUND_LSC_INSUFFICIENT,
                            "LSC部分退款失败: " + (result == null ? "账本服务无响应" : result.getMessage()));
                }
            }
            // 2. 部分退回人民币
            if (refundRmb.compareTo(BigDecimal.ZERO) > 0) {
                invokeRmbRefund(order.getOrderNo(), refundRmb);
            }
            order.setRefundLscAmount(totalRefundLsc);
            order.setRefundRmbAmount(totalRefundRmb);
            // 全部退完则置为已退款，否则部分退款
            boolean fullyRefunded = totalRefundLsc == order.getLscAmount()
                    && totalRefundRmb.compareTo(order.getRmbAmount()) == 0;
            order.setStatus(fullyRefunded
                    ? OrderStatusEnum.REFUNDED.getCode()
                    : OrderStatusEnum.PARTIAL_REFUNDED.getCode());
            orderMapper.updateById(order);
            log.info("订单部分退款完成 orderNo={} refundLsc={} refundRmb={} totalRefundLsc={} totalRefundRmb={}",
                    order.getOrderNo(), refundLsc, refundRmb, totalRefundLsc, totalRefundRmb);
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("订单退款被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Order getByOrderNo(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        // 退款金额字段兜底，防止历史数据/外部导入数据 NULL 时自动拆箱 NPE
        if (order.getRefundLscAmount() == null) {
            order.setRefundLscAmount(0L);
        }
        if (order.getRefundRmbAmount() == null) {
            order.setRefundRmbAmount(BigDecimal.ZERO);
        }
        return order;
    }

    @Override
    public IPage<Order> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status) {
        return listOrders(pageNum, pageSize, userId, status, null, null, null, null);
    }

    @Override
    public IPage<Order> listOrders(Integer pageNum, Integer pageSize, Long userId, Integer status,
                                   String orderNo, Integer orderType, String startDate, String endDate) {
        Page<Order> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.and(w -> w.eq(Order::getConsumerId, userId)
                    .or().eq(Order::getMerchantId, userId));
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(orderNo)) {
            wrapper.like(Order::getOrderNo, orderNo);
        }
        if (orderType != null) {
            wrapper.eq(Order::getOrderType, orderType);
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(startDate)) {
            wrapper.ge(Order::getCreatedAt, java.time.LocalDate.parse(startDate).atStartOfDay());
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(endDate)) {
            wrapper.le(Order::getCreatedAt, java.time.LocalDate.parse(endDate).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(page, wrapper);
    }

    @Override
    public java.util.Map<String, Object> dailySummary(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        // 已支付(1)/已完成(2)/已退款(4)/部分退款(5) 视为已发生支付，纳入对账基准
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Order::getPayTime, target.atStartOfDay())
                .lt(Order::getPayTime, target.plusDays(1).atStartOfDay())
                .in(Order::getStatus,
                        OrderStatusEnum.PAID.getCode(),
                        OrderStatusEnum.COMPLETED.getCode(),
                        OrderStatusEnum.REFUNDED.getCode(),
                        OrderStatusEnum.PARTIAL_REFUNDED.getCode());
        List<Order> orders = orderMapper.selectList(wrapper);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Order o : orders) {
            if (o.getTotalPrice() != null) {
                totalAmount = totalAmount.add(o.getTotalPrice());
            }
        }
        result.put("totalAmount", totalAmount);
        result.put("totalCount", (long) orders.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(String orderNo, Long operatorId, String reason) {
        Order order = getByOrderNo(orderNo);
        // 仅退款中状态的订单可拒绝退款(恢复为已完成)
        if (order.getStatus() != null && order.getStatus() == 4) {
            order.setStatus(OrderStatusEnum.COMPLETED.getCode());
            orderMapper.updateById(order);
            log.info("[拒绝退款] orderNo={} operatorId={} reason={}", orderNo, operatorId, reason);
        }
    }

    @Override
    public java.util.Map<String, Object> statsToday(Long merchantId) {
        java.time.LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Order> baseWrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            baseWrapper.eq(Order::getMerchantId, merchantId);
        }
        baseWrapper.ge(Order::getCreatedAt, today.atStartOfDay())
                .lt(Order::getCreatedAt, today.plusDays(1).atStartOfDay());
        List<Order> todayOrders = orderMapper.selectList(baseWrapper);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("todayOrderCount", (long) todayOrders.size());
        BigDecimal todayRevenue = BigDecimal.ZERO;
        for (Order o : todayOrders) {
            if (o.getTotalPrice() != null && o.getStatus() != null
                    && (o.getStatus() == 1 || o.getStatus() == 2)) {
                todayRevenue = todayRevenue.add(o.getTotalPrice());
            }
        }
        result.put("todayRevenue", todayRevenue);

        // 待发货/待退款统计
        LambdaQueryWrapper<Order> pendingWrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            pendingWrapper.eq(Order::getMerchantId, merchantId);
        }
        pendingWrapper.eq(Order::getStatus, OrderStatusEnum.PAID.getCode());
        result.put("pendingShipCount", orderMapper.selectCount(pendingWrapper));

        LambdaQueryWrapper<Order> refundWrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            refundWrapper.eq(Order::getMerchantId, merchantId);
        }
        refundWrapper.eq(Order::getStatus, OrderStatusEnum.PARTIAL_REFUNDED.getCode());
        result.put("pendingRefundCount", orderMapper.selectCount(refundWrapper));
        return result;
    }

    /**
     * 唤起人民币支付(模拟)
     * <p>实际场景调用支付机构(支付宝/微信)统一下单接口。</p>
     */
    private void invokeRmbPayment(String orderNo, BigDecimal amount) {
        log.info("[模拟]唤起人民币支付 orderNo={} amount={}", orderNo, amount);
    }

    /**
     * 人民币退款(模拟)
     * <p>实际场景调用支付机构退款接口。</p>
     */
    private void invokeRmbRefund(String orderNo, BigDecimal amount) {
        log.info("[模拟]人民币退款 orderNo={} amount={}", orderNo, amount);
    }

    /**
     * 生成订单号：ORD + yyyyMMddHHmmss + 雪花后6位
     */
    private String generateOrderNo(long snowflake) {
        return "ORD" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss")
                + String.format("%06d", Math.abs(snowflake % 1_000_000));
    }
}
