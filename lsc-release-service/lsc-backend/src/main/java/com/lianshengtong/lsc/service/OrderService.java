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

        Orders order = new Orders();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(product.getMerchantId());
        order.setProductId(productId);
        order.setProductName(product.getProductName());
        order.setOrderType(0);
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

        Orders order = new Orders();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setOrderType(1);
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
     * 订单退款：LSC 从商家退回消费者，订单状态置为已退款(4)
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

        // LSC 退回：商家 → 消费者
        if (order.getLscAmount() != null && order.getLscAmount() > 0) {
            lscAccountService.transfer(order.getMerchantId(), order.getUserId(),
                    order.getLscAmount(), 6, "REFUND-" + orderNo);
        }

        order.setStatus(4); // 已退款
        ordersMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("refundedLsc", order.getLscAmount());
        result.put("refundedRmb", order.getRmbAmount());
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
