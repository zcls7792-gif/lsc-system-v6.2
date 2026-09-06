package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.Orders;
import com.lianshengtong.lsc.entity.Product;
import com.lianshengtong.lsc.mapper.OrdersMapper;
import com.lianshengtong.lsc.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrdersMapper ordersMapper;
    private final ProductMapper productMapper;
    private final LscAccountService lscAccountService;

    @Transactional
    public Map<String, Object> createOrder(Long userId, Long productId, Integer quantity,
                                           Long lscAmount, Long addressId) {
        if (quantity == null || quantity <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "数量必须大于 0");
        if (lscAmount == null || lscAmount < 0) lscAmount = 0L;

        Product product = productMapper.selectById(productId);
        if (product == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        // LSC 抵扣不能超过总价（LSC 为整数，取总价整数部分）
        long lscCap = totalPrice.longValue();
        if (lscAmount > lscCap) lscAmount = lscCap;

        BigDecimal rmbAmount = totalPrice.subtract(BigDecimal.valueOf(lscAmount));
        String orderNo = "ORD" + System.currentTimeMillis();

        // 扣减 LSC（消费者→商家）
        if (lscAmount > 0) {
            lscAccountService.checkFlowPermission(userId, product.getMerchantId());
            lscAccountService.transfer(userId, product.getMerchantId(), lscAmount, 4, orderNo);
        }

        // 首单检测：用户是否已有已完成订单
        Long prevOrderCount = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getUserId, userId).gt(Orders::getStatus, 0));
        boolean isFirst = prevOrderCount == 0 && totalPrice.compareTo(new BigDecimal("10")) >= 0;

        // 支付类型：0=纯人民币, 1=LSC全额, 2=混合支付
        int paymentType;
        if (lscAmount == 0) paymentType = 0;
        else if (lscAmount >= totalPrice.longValue()) paymentType = 1;
        else paymentType = 2;

        Orders order = new Orders();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(product.getMerchantId());
        order.setProductId(productId);
        order.setProductName(product.getProductName());
        order.setOrderType(0);
        order.setPaymentType(paymentType);
        order.setIsFirstOrder(isFirst ? 1 : 0);
        order.setTotalPrice(totalPrice);
        order.setLscAmount(lscAmount);
        order.setRmbAmount(rmbAmount);
        order.setStatus(1); // 直接标记已支付（LSC部分已扣，人民币部分由收银台处理）
        order.setAddressId(addressId);
        order.setCreatedAt(LocalDateTime.now());
        ordersMapper.insert(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("totalPrice", totalPrice);
        result.put("lscAmount", lscAmount);
        result.put("rmbAmount", rmbAmount);
        result.put("status", 1);
        result.put("isFirstOrder", isFirst);
        result.put("paymentType", paymentType);
        result.put("payUrl", rmbAmount.compareTo(BigDecimal.ZERO) > 0
                ? "https://pay.lsc.com/cashier?orderNo=" + orderNo : "");
        return result;
    }

    @Transactional
    public Map<String, Object> payOffline(Long userId, Long merchantId, BigDecimal amount, Long lscAmount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "金额必须大于 0");
        if (lscAmount == null || lscAmount < 0) lscAmount = 0L;
        // 线下消费：消费者→商家
        long lscCap = amount.longValue();
        if (lscAmount > lscCap) lscAmount = lscCap;
        BigDecimal rmbAmount = amount.subtract(BigDecimal.valueOf(lscAmount));
        String orderNo = "OFF" + System.currentTimeMillis();

        if (lscAmount > 0) {
            lscAccountService.checkFlowPermission(userId, merchantId);
            lscAccountService.transfer(userId, merchantId, lscAmount, 5, orderNo);
        }

        // 首单检测
        Long prevOrderCount = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getUserId, userId).gt(Orders::getStatus, 0));
        boolean isFirst = prevOrderCount == 0 && amount.compareTo(new BigDecimal("10")) >= 0;

        // 支付类型
        int paymentType;
        if (lscAmount == 0) paymentType = 0;
        else if (lscAmount >= amount.longValue()) paymentType = 1;
        else paymentType = 2;

        Orders order = new Orders();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setOrderType(1);
        order.setPaymentType(paymentType);
        order.setIsFirstOrder(isFirst ? 1 : 0);
        order.setTotalPrice(amount);
        order.setLscAmount(lscAmount);
        order.setRmbAmount(rmbAmount);
        order.setStatus(1);
        order.setCreatedAt(LocalDateTime.now());
        ordersMapper.insert(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("totalPrice", amount);
        result.put("lscAmount", lscAmount);
        result.put("rmbAmount", rmbAmount);
        result.put("status", 1);
        return result;
    }

    public Page<Orders> list(Long userId, Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<Orders> qw = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .orderByDesc(Orders::getCreatedAt);
        if (status != null) qw.eq(Orders::getStatus, status);
        return ordersMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /**
     * 订单退款（方案文档 P0 规则）：
     * - 首单不退（is_first_order=1）
     * - LSC订单不退（lsc_amount > 0，含LSC全额和混合支付）
     * - 仅纯人民币支付(payment_type=0)的非首单订单可退款
     * - 退款仅涉及人民币原路退回，LSC不退回（仅触发LSC发行回滚 type=9）
     */
    @Transactional
    public Map<String, Object> refund(Long userId, String orderNo) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        // 仅订单所属消费者可发起退款
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "仅已支付订单可退款");
        }
        // 首单不退
        if (order.getIsFirstOrder() != null && order.getIsFirstOrder() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "首单消费不支持退款");
        }
        // LSC订单不退（lscAmount > 0 即含 LSC 支付）
        if (order.getLscAmount() != null && order.getLscAmount() > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "使用LSC的订单不支持退款");
        }
        // 仅纯人民币支付可退
        if (order.getPaymentType() != null && order.getPaymentType() != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "仅纯人民币支付的非首单订单支持退款");
        }

        // 纯人民币退款：LSC 发行回滚（type=9），人民币原路退回（由支付机构处理）
        // 此处仅记录 LSC 发行回滚流水，实际人民币退回由支付机构接口完成
        lscAccountService.recordRefundRollback(userId, order.getOrderNo(), order.getTotalPrice());

        order.setStatus(4); // 已退款
        order.setRefundRmbAmount(order.getRmbAmount());
        order.setRefundLscAmount(0L);
        order.setCompletedAt(LocalDateTime.now());
        ordersMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("refundedRmb", order.getRmbAmount());
        result.put("refundType", "纯人民币原路退回");
        result.put("status", 4);
        return result;
    }

    public Orders detail(String orderNo) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return order;
    }
}
