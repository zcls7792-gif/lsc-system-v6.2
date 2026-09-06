package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.Merchant;
import com.lianshengtong.lsc.entity.NhLevel;
import com.lianshengtong.lsc.entity.NhRecord;
import com.lianshengtong.lsc.mapper.MerchantMapper;
import com.lianshengtong.lsc.mapper.NhLevelMapper;
import com.lianshengtong.lsc.mapper.NhRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NhService {

    private final MerchantMapper merchantMapper;
    private final NhLevelMapper nhLevelMapper;
    private final NhRecordMapper nhRecordMapper;
    private final LscAccountService lscAccountService;

    @Value("${lsc.nh.ratio:0.87}")
    private BigDecimal nhRatio;

    public Map<String, Object> getQuota(Long userId) {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (merchant == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        // 资格校验：三证
        boolean qualified = merchant.getBusinessLicenseUrl() != null
                && merchant.getCorporateAccountNo() != null
                && merchant.getRegulatoryAgreementSigned() != null
                && merchant.getRegulatoryAgreementSigned() == 1
                && merchant.getAuditStatus() != null && merchant.getAuditStatus() == 1;

        // 档位额度
        NhLevel level = nhLevelMapper.selectOne(
                new LambdaQueryWrapper<NhLevel>().eq(NhLevel::getLevel, merchant.getLevel()));
        Long dailyLimit = level != null ? level.getDailyLimit() : 275L;

        // 今日已核销
        Long usedToday = nhRecordMapper.selectList(
                new LambdaQueryWrapper<NhRecord>()
                        .eq(NhRecord::getMerchantId, merchant.getId())
                        .eq(NhRecord::getNhDate, LocalDate.now())
                        .eq(NhRecord::getStatus, 2)
        ).stream().mapToLong(NhRecord::getLscAmount).sum();

        // 最近一次核销日期
        NhRecord last = nhRecordMapper.selectOne(
                new LambdaQueryWrapper<NhRecord>()
                        .eq(NhRecord::getMerchantId, merchant.getId())
                        .eq(NhRecord::getStatus, 2)
                        .orderByDesc(NhRecord::getCreatedAt)
                        .last("LIMIT 1"));

        Map<String, Object> result = new HashMap<>();
        result.put("merchantId", merchant.getId());
        result.put("qualified", qualified);
        result.put("businessLicenseVerified", merchant.getBusinessLicenseUrl() != null);
        result.put("corporateAccountBound", merchant.getCorporateAccountNo() != null);
        result.put("regulatoryAgreementSigned", merchant.getRegulatoryAgreementSigned() != null && merchant.getRegulatoryAgreementSigned() == 1);
        result.put("level", merchant.getLevel());
        result.put("monthlyRevenue", merchant.getMonthlyRevenue());
        result.put("dailyLimit", dailyLimit);
        result.put("usedToday", usedToday);
        result.put("remainingToday", Math.max(0, dailyLimit - usedToday));
        result.put("lastNhDate", last != null ? last.getNhDate() : null);
        result.put("creditScore", merchant.getCreditScore());
        return result;
    }

    @Transactional
    public Map<String, Object> apply(Long userId, Long lscAmount) {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (merchant == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        // 1. 资格校验
        Map<String, Object> quota = getQuota(userId);
        if (!Boolean.TRUE.equals(quota.get("qualified"))) {
            throw new BusinessException(ErrorCode.NH_NOT_QUALIFIED);
        }

        // 2. 每日限 1 次
        if (quota.get("lastNhDate") != null && LocalDate.now().equals(quota.get("lastNhDate"))) {
            throw new BusinessException(ErrorCode.NH_ALREADY_DONE_TODAY);
        }

        // 3. 额度校验
        Long remaining = (Long) quota.get("remainingToday");
        if (lscAmount > remaining) {
            throw new BusinessException(ErrorCode.NH_DAILY_LIMIT_EXCEEDED);
        }

        // 4. LSC 余额校验（在 transfer 中处理）
        // 5. 核销：100 LSC = ¥87
        BigDecimal cashAmount = BigDecimal.valueOf(lscAmount)
                .multiply(nhRatio).setScale(2, RoundingMode.HALF_UP);

        String orderNo = "NH" + System.currentTimeMillis();

        // 6. 扣减并销毁 LSC（toUserId=null 表示销毁）
        lscAccountService.transfer(userId, null, lscAmount, 7, orderNo);

        // 7. 写入核销记录
        NhRecord record = new NhRecord();
        record.setOrderNo(orderNo);
        record.setMerchantId(merchant.getId());
        record.setLscAmount(lscAmount);
        record.setCashAmount(cashAmount);
        record.setStatus(2);
        record.setNhDate(LocalDate.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setCompletedAt(LocalDateTime.now());
        nhRecordMapper.insert(record);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("lscAmount", lscAmount);
        result.put("cashAmount", cashAmount);
        result.put("status", 2);
        return result;
    }

    public Object getRecords(Long userId, int pageNo, int pageSize) {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        return nhRecordMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<NhRecord>()
                        .eq(NhRecord::getMerchantId, merchant.getId())
                        .orderByDesc(NhRecord::getCreatedAt));
    }
}
