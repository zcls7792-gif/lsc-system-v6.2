package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.B2bOrder;
import com.lianshengtong.lsc.mapper.B2bOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class B2bService {

    private final B2bOrderMapper b2bOrderMapper;
    private final LscAccountService lscAccountService;

    public Map<String, Object> createOrder(Long fromMerchantUserId, Long toMerchantId,
                                           String tradeDescription, java.math.BigDecimal totalAmountRmb,
                                           Long lscAmount, String contractNo, String tradeEvidenceUrls) {
        String orderNo = "B2B" + System.currentTimeMillis();
        B2bOrder order = new B2bOrder();
        order.setOrderNo(orderNo);
        order.setFromMerchantId(fromMerchantUserId);
        order.setToMerchantId(toMerchantId);
        order.setTradeDescription(tradeDescription);
        order.setTotalAmountRmb(totalAmountRmb);
        order.setLscAmount(lscAmount);
        order.setContractNo(contractNo);
        order.setTradeEvidenceUrls(tradeEvidenceUrls);
        order.setAiVerificationResult(0); // AI 默认判定真实
        order.setStatus(0); // 待确认
        order.setCreatedAt(LocalDateTime.now());
        b2bOrderMapper.insert(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("status", 0);
        return result;
    }

    @Transactional
    public Map<String, Object> confirm(String orderNo, boolean confirmed, Long confirmUserId) {
        B2bOrder order = b2bOrderMapper.selectOne(
                new LambdaQueryWrapper<B2bOrder>().eq(B2bOrder::getOrderNo, orderNo));
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (!confirmed) {
            order.setStatus(4); // 取消
            b2bOrderMapper.updateById(order);
            return Map.of("status", 4);
        }
        // AI 核验未通过则禁止流转
        if (order.getAiVerificationResult() == 1 || order.getAiVerificationResult() == 3) {
            throw new BusinessException(ErrorCode.B2B_AI_VERIFY_FAILED);
        }
        order.setStatus(1); // 已确认
        order.setConfirmedBy("user-" + confirmUserId);
        b2bOrderMapper.updateById(order);

        // 执行 LSC 流转：商家→商家
        lscAccountService.transfer(order.getFromMerchantId(), order.getToMerchantId(),
                order.getLscAmount(), 8, order.getOrderNo());
        order.setStatus(2); // 已流转
        b2bOrderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("status", 2);
        return result;
    }

    public void aiVerify(Long orderId, Integer aiResult) {
        B2bOrder order = b2bOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        order.setAiVerificationResult(aiResult);
        b2bOrderMapper.updateById(order);
    }

    public Page<B2bOrder> list(Long merchantUserId, Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<B2bOrder> qw = new LambdaQueryWrapper<B2bOrder>()
                .and(w -> w.eq(B2bOrder::getFromMerchantId, merchantUserId)
                        .or().eq(B2bOrder::getToMerchantId, merchantUserId))
                .orderByDesc(B2bOrder::getCreatedAt);
        if (status != null) qw.eq(B2bOrder::getStatus, status);
        return b2bOrderMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }
}
