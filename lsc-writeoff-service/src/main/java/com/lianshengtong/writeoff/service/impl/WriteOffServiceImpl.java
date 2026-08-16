package com.lianshengtong.writeoff.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.enums.MerchantPenaltyStatusEnum;
import com.lianshengtong.common.enums.WriteOffStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.IdempotentKeyGenerator;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import com.lianshengtong.writeoff.dto.MerchantInfoDTO;
import com.lianshengtong.writeoff.dto.WriteOffApplyDTO;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;
import com.lianshengtong.writeoff.feign.LscLedgerFeignClient;
import com.lianshengtong.writeoff.feign.MerchantFeignClient;
import com.lianshengtong.writeoff.mapper.MerchantNhRecordMapper;
import com.lianshengtong.writeoff.service.WriteOffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 商家核销服务实现
 * <p>
 * 核销流程(四重校验 + 资金划拨 + LSC销毁)：
 * <ol>
 *   <li>资格校验：penalty_status 为 0(正常)或 1(一级处罚，限额50%)，二级及以上不具备资格</li>
 *   <li>次数校验：每日限1次(查 last_nh_date 是否为今日)</li>
 *   <li>限额校验：核销数量 &lt;= 当日有效限额(正常=全量，一级=50%)</li>
 *   <li>余额校验：可用 LSC 余额充足</li>
 *   <li>现金计算：cash_amount = lsc_amount * 87 / 100</li>
 *   <li>调用支付机构划拨资金(模拟)</li>
 *   <li>调用账本服务扣减 LSC 并销毁</li>
 *   <li>记录核销流水，更新 merchant_extensions.last_nh_date</li>
 * </ol>
 * 幂等通过 order_no 唯一索引 + version 乐观锁双重校验保障。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WriteOffServiceImpl implements WriteOffService {

    private final MerchantNhRecordMapper merchantNhRecordMapper;
    private final LscLedgerFeignClient lscLedgerFeignClient;
    private final MerchantFeignClient merchantFeignClient;
    private final RedissonClient redissonClient;

    /**
     * 自注入代理对象，用于触发 REQUIRES_NEW 等事务 AOP 行为(自调用不会走代理)。
     */
    @Autowired
    @Lazy
    private WriteOffService self;

    @Value("${lsc.writeoff.cash-ratio-numerator:87}")
    private int cashRatioNumerator;

    @Value("${lsc.writeoff.cash-ratio-denominator:100}")
    private int cashRatioDenominator;

    @Value("${lsc.writeoff.level1-limit-ratio:50}")
    private int level1LimitRatio;

    @Value("${lsc.writeoff.lock-wait-ms:3000}")
    private long lockWaitMs;

    @Value("${lsc.writeoff.lock-lease-ms:10000}")
    private long lockLeaseMs;

    private static final String LOCK_PREFIX = "lock:writeoff:merchant:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantNhRecord applyWriteOff(WriteOffApplyDTO dto) {
        Long merchantId = dto.getMerchantId();
        Long lscAmount = dto.getLscAmount();

        // 商家级分布式锁，防止同一商家并发核销(保障每日1次幂等)
        RLock lock = redissonClient.getLock(LOCK_PREFIX + merchantId);
        try {
            if (!lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS)) {
                throw new BizException("核销处理中，请稍后重试");
            }

            // 1. 查询商家信息
            R<MerchantInfoDTO> merchantR = merchantFeignClient.getMerchantInfo(merchantId);
            if (merchantR == null || !merchantR.isSuccess() || merchantR.getData() == null) {
                throw new BizException(ResultCode.MERCHANT_NOT_QUALIFIED, "商家信息查询失败");
            }
            MerchantInfoDTO merchant = merchantR.getData();

            // 2. 资格校验：仅正常(0)或一级处罚(1)可核销
            Integer penaltyStatus = merchant.getPenaltyStatus() == null
                    ? MerchantPenaltyStatusEnum.NORMAL.getCode() : merchant.getPenaltyStatus();
            if (penaltyStatus >= MerchantPenaltyStatusEnum.LEVEL2.getCode()) {
                throw new BizException(ResultCode.MERCHANT_NOT_QUALIFIED,
                        "商家处罚等级(" + penaltyStatus + ")不具备核销资格");
            }

            // 3. 次数校验：每日限1次
            LocalDate today = LocalDate.now();
            if (merchant.getLastNhDate() != null && merchant.getLastNhDate().equals(today)) {
                throw new BizException(ResultCode.WRITE_OFF_DAILY_LIMIT);
            }

            // 4. 限额校验：核销数量 <= 当日有效限额
            int dailyNhLimit = merchant.getDailyNhLimit() == null ? 0 : merchant.getDailyNhLimit();
            int effectiveLimit = dailyNhLimit;
            if (penaltyStatus == MerchantPenaltyStatusEnum.LEVEL1.getCode()) {
                // 一级处罚按 50% 限额
                effectiveLimit = dailyNhLimit * level1LimitRatio / 100;
            }
            if (lscAmount > effectiveLimit) {
                throw new BizException(ResultCode.WRITE_OFF_LIMIT_EXCEEDED,
                        "核销数量" + lscAmount + "超过当日限额" + effectiveLimit);
            }

            // 5. 余额校验：可用 LSC 余额充足
            R<Map<String, Object>> balanceR = lscLedgerFeignClient.getBalance(merchantId);
            if (balanceR == null || !balanceR.isSuccess() || balanceR.getData() == null) {
                throw new BizException("账本余额查询失败");
            }
            long totalAvailable = toLong(balanceR.getData().get("totalAvailable"));
            if (totalAvailable < lscAmount) {
                throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
            }

            // 6. 现金计算：cash_amount = lsc_amount * 87 / 100
            BigDecimal cashAmount = BigDecimal.valueOf(lscAmount)
                    .multiply(BigDecimal.valueOf(cashRatioNumerator))
                    .divide(BigDecimal.valueOf(cashRatioDenominator), 2, RoundingMode.HALF_UP);

            // 7. 创建核销记录(待处理)，order_no/idempotent_key 唯一索引保障幂等
            MerchantNhRecord record = new MerchantNhRecord();
            long snowflake = SnowflakeIdUtil.id();
            record.setId(snowflake);
            record.setOrderNo(generateOrderNo(snowflake));
            record.setIdempotentKey(IdempotentKeyGenerator.generate("WRITEOFF", merchantId));
            record.setMerchantId(merchantId);
            record.setLscAmount(lscAmount);
            record.setCashAmount(cashAmount);
            record.setAvailableBefore(totalAvailable);
            record.setAvailableAfter(totalAvailable - lscAmount);
            // 监管账户余额(由支付机构返回，此处模拟)
            BigDecimal fundBefore = querySimulatedFundBalance(merchant.getMainAccountNo());
            record.setFundBefore(fundBefore);
            record.setFundAfter(fundBefore.add(cashAmount));
            record.setVersion(1);
            record.setStatus(WriteOffStatusEnum.PROCESSING.getCode());
            merchantNhRecordMapper.insert(record);

            try {
                // 8. 调用支付机构划拨资金(模拟)
                invokeFundTransfer(record.getOrderNo(), merchantId, cashAmount, merchant.getMainAccountNo());

                // 9. 调用账本服务扣减 LSC 并销毁
                LscLedgerOpDTO opDTO = LscLedgerOpDTO.builder()
                        .idempotentKey(IdempotentKeyGenerator.generate("WRITEOFF_DESTROY", merchantId))
                        .transactionType(LscTransactionTypeEnum.MERCHANT_WRITE_OFF.getCode())
                        .userId(merchantId)
                        .availableDelta(-lscAmount)
                        .orderNo(record.getOrderNo())
                        .remark("商家核销销毁")
                        .build();
                R<Map<String, Object>> writeOffR = lscLedgerFeignClient.writeOffLsc(opDTO);
                if (writeOffR == null || !writeOffR.isSuccess()) {
                    throw new BizException(ResultCode.SEATA_TRANSACTION_EXCEPTION,
                            "LSC销毁失败: " + (writeOffR == null ? "账本服务无响应" : writeOffR.getMessage()));
                }

                // 10. 更新核销记录为成功(乐观锁 version 校验)
                record.setStatus(WriteOffStatusEnum.SUCCESS.getCode());
                record.setCompletedAt(LocalDateTime.now());
                int rows = merchantNhRecordMapper.updateById(record);
                if (rows == 0) {
                    throw new BizException("核销记录更新失败(乐观锁冲突)，请核对账本后人工处理");
                }
            } catch (RuntimeException e) {
                // 标记失败并记录原因(通过 self 代理在新事务中执行，避免主事务回滚导致失败记录一并回滚)
                try {
                    self.markRecordFailed(record.getId(), record.getVersion(), e.getMessage());
                } catch (RuntimeException ex) {
                    log.error("标记核销记录失败时异常 orderNo={} recordId={}", record.getOrderNo(), record.getId(), ex);
                }
                log.error("核销失败 orderNo={} merchantId={} err={}", record.getOrderNo(), merchantId, e.getMessage());
                throw e;
            }

            // 11. 更新商家最近核销日期
            try {
                merchantFeignClient.updateLastNhDate(merchantId, today);
            } catch (RuntimeException e) {
                log.warn("更新商家最近核销日期失败 merchantId={} err={}", merchantId, e.getMessage());
            }
            log.info("核销成功 orderNo={} merchantId={} lscAmount={} cashAmount={}",
                    record.getOrderNo(), merchantId, lscAmount, cashAmount);
            return record;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("核销被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public MerchantNhRecord getByOrderNo(String orderNo) {
        MerchantNhRecord record = merchantNhRecordMapper.selectOne(
                new LambdaQueryWrapper<MerchantNhRecord>().eq(MerchantNhRecord::getOrderNo, orderNo));
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "核销记录不存在");
        }
        return record;
    }

    @Override
    public MerchantNhRecord getById(Long id) {
        MerchantNhRecord record = merchantNhRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "核销记录不存在");
        }
        return record;
    }

    @Override
    public IPage<MerchantNhRecord> listRecords(Integer pageNum, Integer pageSize, Long merchantId, Integer status) {
        return listRecords(pageNum, pageSize, merchantId, status, null, null, null);
    }

    @Override
    public IPage<MerchantNhRecord> listRecords(Integer pageNum, Integer pageSize, Long merchantId, Integer status,
                                               String batchNo, String startDate, String endDate) {
        Page<MerchantNhRecord> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<MerchantNhRecord> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(MerchantNhRecord::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(MerchantNhRecord::getStatus, status);
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(batchNo)) {
            wrapper.like(MerchantNhRecord::getOrderNo, batchNo);
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(startDate)) {
            wrapper.ge(MerchantNhRecord::getCreatedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(endDate)) {
            wrapper.le(MerchantNhRecord::getCreatedAt, LocalDate.parse(endDate).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(MerchantNhRecord::getCreatedAt);
        return merchantNhRecordMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> stats(Long merchantId, String startDate, String endDate) {
        LambdaQueryWrapper<MerchantNhRecord> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(MerchantNhRecord::getMerchantId, merchantId);
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(startDate)) {
            wrapper.ge(MerchantNhRecord::getCreatedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(endDate)) {
            wrapper.le(MerchantNhRecord::getCreatedAt, LocalDate.parse(endDate).atTime(23, 59, 59));
        }
        List<MerchantNhRecord> records = merchantNhRecordMapper.selectList(wrapper);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        long totalCount = records.size();
        long totalLsc = records.stream().mapToLong(r -> r.getLscAmount() == null ? 0 : r.getLscAmount()).sum();
        BigDecimal totalCash = records.stream()
                .map(r -> r.getCashAmount() == null ? BigDecimal.ZERO : r.getCashAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("totalCount", totalCount);
        result.put("totalLscAmount", totalLsc);
        result.put("totalCashAmount", totalCash);
        // 按状态分组统计
        Map<Integer, Long> byStatus = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getStatus() == null ? -1 : r.getStatus(),
                        java.util.stream.Collectors.counting()));
        result.put("byStatus", byStatus);
        return result;
    }

    @Override
    public Map<String, Object> quota(Long merchantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 从 user-service 拉取商家限额信息
        R<MerchantInfoDTO> merchantR = merchantFeignClient.getMerchantInfo(merchantId);
        if (merchantR == null || !merchantR.isSuccess() || merchantR.getData() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商家信息不存在");
        }
        MerchantInfoDTO merchant = merchantR.getData();
        Integer dailyLimit = merchant.getDailyNhLimit() == null ? 80 : merchant.getDailyNhLimit();

        // 统计今日已核销 LSC
        LambdaQueryWrapper<MerchantNhRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantNhRecord::getMerchantId, merchantId)
                .eq(MerchantNhRecord::getStatus, WriteOffStatusEnum.SUCCESS.getCode())
                .ge(MerchantNhRecord::getCreatedAt, LocalDate.now().atStartOfDay())
                .lt(MerchantNhRecord::getCreatedAt, LocalDate.now().plusDays(1).atStartOfDay());
        List<MerchantNhRecord> todayRecords = merchantNhRecordMapper.selectList(wrapper);
        long todayUsed = todayRecords.stream()
                .mapToLong(r -> r.getLscAmount() == null ? 0 : r.getLscAmount())
                .sum();

        result.put("dailyLimit", dailyLimit);
        result.put("todayUsed", todayUsed);
        result.put("todayRemaining", Math.max(0, dailyLimit - todayUsed));
        result.put("nhLimitLevel", calculateNhLimitLevel(dailyLimit));
        result.put("cashRate", new BigDecimal("0.87"));
        result.put("regulatoryBalance", BigDecimal.ZERO); // 监管账户余额需对接资金服务
        result.put("lastNhDate", merchant.getLastNhDate());
        return result;
    }

    /** 根据每日核销限额推算档位 1-16 */
    private int calculateNhLimitLevel(int dailyNhLimit) {
        // 限额档位:80=1, 160=2, ..., 每档翻倍, 最高16档
        if (dailyNhLimit <= 0) return 0;
        int level = 1;
        int limit = 80;
        while (limit < dailyNhLimit && level < 16) {
            limit *= 2;
            level++;
        }
        return level;
    }

    /**
     * 调用支付机构划拨资金(模拟)
     * <p>实际场景调用支付机构(银联/网商)资金划拨接口，将监管账户资金划拨至商家主账户。</p>
     */
    private void invokeFundTransfer(String orderNo, Long merchantId, BigDecimal cashAmount, String accountNo) {
        log.info("[模拟]支付机构资金划拨 orderNo={} merchantId={} accountNo={} cashAmount={}",
                orderNo, merchantId, accountNo, cashAmount);
    }

    /**
     * 查询监管账户余额(模拟)
     * <p>实际场景调用支付机构查询接口。</p>
     */
    private BigDecimal querySimulatedFundBalance(String accountNo) {
        log.info("[模拟]查询监管账户余额 accountNo={}", accountNo);
        return BigDecimal.ZERO;
    }

    private long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(val));
    }

    /**
     * 生成核销订单号：NH + yyyyMMddHHmmss + 雪花后6位
     */
    private String generateOrderNo(long snowflake) {
        return "NH" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss")
                + String.format("%06d", Math.abs(snowflake % 1_000_000));
    }

    /**
     * 标记核销记录为失败(在新事务中执行)。
     * <p>
     * 主事务因异常即将回滚时，需要将失败原因持久化以便运维追踪。
     * 通过 REQUIRES_NEW 传播级别开启新事务，确保失败记录落库不被回滚。
     * 调用方需通过 Spring 代理对象(self)调用，自调用不会触发 AOP。
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRecordFailed(Long recordId, Integer version, String failReason) {
        MerchantNhRecord failUpdate = new MerchantNhRecord();
        failUpdate.setId(recordId);
        failUpdate.setVersion(version);
        failUpdate.setStatus(WriteOffStatusEnum.FAILED.getCode());
        failUpdate.setFailReason(failReason);
        merchantNhRecordMapper.updateById(failUpdate);
    }
}
