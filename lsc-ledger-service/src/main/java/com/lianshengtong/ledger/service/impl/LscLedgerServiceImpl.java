package com.lianshengtong.ledger.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.enums.AvailableLscStatusEnum;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.IdempotentKeyGenerator;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.OptimisticLockHelper;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;
import com.lianshengtong.ledger.mapper.AvailableLscDetailMapper;
import com.lianshengtong.ledger.mapper.LscAccountMapper;
import com.lianshengtong.ledger.mapper.LscTransactionMapper;
import com.lianshengtong.ledger.service.LscAccountService;
import com.lianshengtong.ledger.service.LscLedgerService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * LSC 账本核心服务实现
 * <p>
 * 所有写操作均保证：
 * <ul>
 *   <li>幂等性：基于 {@code lsc_transactions.idempotent_key} 唯一索引 + 操作前预查</li>
 *   <li>并发安全：Redisson 分布式锁(按 userId 排序加锁避免死锁) + MyBatis-Plus 乐观锁(version)</li>
 *   <li>原子事务：通过 {@link TransactionTemplate} 编程式事务保证锁内多表操作原子提交</li>
 *   <li>流水记录：每笔操作落一条流水，含操作前/后锁定与可用余额快照</li>
 * </ul>
 * </p>
 *
 * @author lsc
 */
@Service
public class LscLedgerServiceImpl implements LscLedgerService {

    private static final Logger log = LoggerFactory.getLogger(LscLedgerServiceImpl.class);

    private final LscAccountMapper accountMapper;
    private final LscTransactionMapper transactionMapper;
    private final AvailableLscDetailMapper detailMapper;
    private final LscAccountService accountService;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${lsc.ledger.lock-wait-ms:3000}")
    private long lockWaitMs;

    @Value("${lsc.ledger.lock-lease-ms:10000}")
    private long lockLeaseMs;

    @Value("${lsc.ledger.b2b-validity-days:365}")
    private int b2bValidityDays;

    @Value("${lsc.ledger.optimistic-lock-enabled:true}")
    private boolean optimisticLockEnabled;

    @Value("${lsc.ledger.expire-batch-size:500}")
    private int expireBatchSize;

    public LscLedgerServiceImpl(LscAccountMapper accountMapper,
                                LscTransactionMapper transactionMapper,
                                AvailableLscDetailMapper detailMapper,
                                LscAccountService accountService,
                                RedissonClient redissonClient,
                                PlatformTransactionManager transactionManager) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.detailMapper = detailMapper;
        this.accountService = accountService;
        this.redissonClient = redissonClient;
        // 自行构造 TransactionTemplate，避免多事务管理器(如 Seata)场景下自动配置失效
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ============================ 消费发行 ============================

    @Override
    public LscAccount issueLsc(Long userId, Long amount, String orderNo) {
        assertPositive(amount);
        // 业务约定：消费发行的 LSC 进入推荐人锁定池，调用方传入推荐人 userId 即可
        return executeWithLock(userId, () -> transactionTemplate.execute(status -> {
            String idemKey = buildIdemKey("ISSUE", orderNo, userId);
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return accountMapper.selectById(userId);
            }
            LscAccount acc = accountService.getOrCreateAccount(userId);
            return applyAccountChange(acc, idemKey, LscTransactionTypeEnum.CONSUMPTION_ISSUE,
                    amount, 0L, null, orderNo, "消费发行LSC到锁定池");
        }));
    }

    // ============================ 每日释放 ============================

    @Override
    public LscAccount releaseLsc(Long userId, Long amount, String orderNo) {
        assertPositive(amount);
        return executeWithLock(userId, () -> transactionTemplate.execute(status -> {
            String idemKey = buildIdemKey("RELEASE", orderNo, userId);
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return accountMapper.selectById(userId);
            }
            LscAccount acc = accountService.getOrCreateAccount(userId);
            // 先写可用明细(有效期365天)，再扣锁定增可用
            writeAvailableDetail(userId, amount, LscTransactionTypeEnum.DAILY_RELEASE.getDesc(), b2bValidityDays);
            return applyAccountChange(acc, idemKey, LscTransactionTypeEnum.DAILY_RELEASE,
                    -amount, amount, null, orderNo, "每日释放:锁定转可用");
        }));
    }

    // ============================ 消费支付 ============================

    @Override
    public LscAccount payLsc(Long consumerId, Long merchantId, Long amount, String orderNo) {
        assertPositive(amount);
        if (consumerId == null || merchantId == null) {
            throw new BizException(400, "收付款方不能为空");
        }
        if (consumerId.equals(merchantId)) {
            throw new BizException(400, "收付款方不能相同");
        }
        if (optimisticLockEnabled) {
            return payLscOptimistically(consumerId, merchantId, amount, orderNo);
        }
        return executeWithLocks(Arrays.asList(consumerId, merchantId), () -> transactionTemplate.execute(status -> {
            String idemKey = buildIdemKey("PAY", orderNo, consumerId);
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return accountMapper.selectById(consumerId);
            }
            LscAccount consumer = accountService.getOrCreateAccount(consumerId);
            LscAccount merchant = accountService.getOrCreateAccount(merchantId);
            long cBeforeAvail = nvl(consumer.getTotalAvailable());
            long mBeforeAvail = nvl(merchant.getTotalAvailable());
            if (cBeforeAvail < amount) {
                throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
            }
            // 扣减消费者可用
            consumer.setTotalAvailable(cBeforeAvail - amount);
            if (accountMapper.updateById(consumer) <= 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "消费者账户更新失败(乐观锁冲突)");
            }
            // 增加商家可用
            merchant.setTotalAvailable(mBeforeAvail + amount);
            if (accountMapper.updateById(merchant) <= 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "商家账户更新失败(乐观锁冲突)");
            }
            // 商家可用明细(有效期365天)
            writeAvailableDetail(merchantId, amount, LscTransactionTypeEnum.MALL_CONSUMPTION.getDesc(), b2bValidityDays);
            // 流水(消费者视角)
            recordTransaction(consumerId, LscTransactionTypeEnum.MALL_CONSUMPTION, amount,
                    nvl(consumer.getTotalLocked()), nvl(consumer.getTotalLocked()),
                    cBeforeAvail, cBeforeAvail - amount, merchantId, orderNo, idemKey, "商城/线下消费支付");
            consumer.setVersion(nvl(consumer.getVersion()) + 1);
            return consumer;
        }));
    }

    /**
     * 乐观锁版支付：单账户高频场景，无分布式锁开销
     */
    private LscAccount payLscOptimistically(Long consumerId, Long merchantId, Long amount, String orderNo) {
        String idemKey = buildIdemKey("PAY_OPT", orderNo, consumerId);
        if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
            return accountMapper.selectById(consumerId);
        }
        Integer rows = OptimisticLockHelper.execute("payLsc", 3, () -> {
            try {
                return transactionTemplate.execute(status -> {
                    LscAccount consumer = accountService.getOrCreateAccount(consumerId);
                    LscAccount merchant = accountService.getOrCreateAccount(merchantId);
                    long cBeforeAvail = nvl(consumer.getTotalAvailable());
                    long mBeforeAvail = nvl(merchant.getTotalAvailable());
                    if (cBeforeAvail < amount) {
                        throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
                    }
                    consumer.setTotalAvailable(cBeforeAvail - amount);
                    int cRows = accountMapper.updateById(consumer);
                    if (cRows <= 0) {
                        throw new OptConflict();
                    }
                    merchant.setTotalAvailable(mBeforeAvail + amount);
                    int mRows = accountMapper.updateById(merchant);
                    if (mRows <= 0) {
                        throw new OptConflict();
                    }
                    writeAvailableDetail(merchantId, amount, LscTransactionTypeEnum.MALL_CONSUMPTION.getDesc(), b2bValidityDays);
                    recordTransaction(consumerId, LscTransactionTypeEnum.MALL_CONSUMPTION, amount,
                            nvl(consumer.getTotalLocked()), nvl(consumer.getTotalLocked()),
                            cBeforeAvail, cBeforeAvail - amount, merchantId, orderNo, idemKey, "商城消费支付(乐观锁)");
                    consumer.setVersion(nvl(consumer.getVersion()) + 1);
                    return 1;
                });
            } catch (OptConflict e) {
                return 0;
            }
        });
        if (rows == null || rows == 0) {
            return accountMapper.selectById(consumerId);
        }
        return accountMapper.selectById(consumerId);
    }

    private static class OptConflict extends RuntimeException {
    }

    // ============================ B2B 流转 ============================

    @Override
    public LscAccount b2bTransfer(Long fromMerchantId, Long toMerchantId, Long amount, String orderNo) {
        assertPositive(amount);
        if (fromMerchantId == null || toMerchantId == null) {
            throw new BizException(400, "流转双方不能为空");
        }
        if (fromMerchantId.equals(toMerchantId)) {
            throw new BizException(400, "流转双方不能相同");
        }
        // 注：商家身份校验由上游服务(user-service)保证，此处执行 1:1 流转与有效期重置
        return executeWithLocks(Arrays.asList(fromMerchantId, toMerchantId), () -> transactionTemplate.execute(status -> {
            String idemKey = buildIdemKey("B2B", orderNo, fromMerchantId);
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return accountMapper.selectById(fromMerchantId);
            }
            LscAccount from = accountService.getOrCreateAccount(fromMerchantId);
            LscAccount to = accountService.getOrCreateAccount(toMerchantId);
            long fBeforeAvail = nvl(from.getTotalAvailable());
            long tBeforeAvail = nvl(to.getTotalAvailable());
            if (fBeforeAvail < amount) {
                throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
            }
            from.setTotalAvailable(fBeforeAvail - amount);
            if (accountMapper.updateById(from) <= 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "发起方账户更新失败(乐观锁冲突)");
            }
            to.setTotalAvailable(tBeforeAvail + amount);
            if (accountMapper.updateById(to) <= 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "接收方账户更新失败(乐观锁冲突)");
            }
            // 接收方可用明细，有效期重置365天
            writeAvailableDetail(toMerchantId, amount, LscTransactionTypeEnum.B2B_TRANSFER.getDesc(), b2bValidityDays);
            recordTransaction(fromMerchantId, LscTransactionTypeEnum.B2B_TRANSFER, amount,
                    nvl(from.getTotalLocked()), nvl(from.getTotalLocked()),
                    fBeforeAvail, fBeforeAvail - amount, toMerchantId, orderNo, idemKey,
                    "B2B流转(1:1),接收方有效期重置365天");
            from.setVersion(nvl(from.getVersion()) + 1);
            return from;
        }));
    }

    // ============================ 商家核销 ============================

    @Override
    public LscAccount writeOffLsc(Long merchantId, Long amount, String orderNo) {
        assertPositive(amount);
        return executeWithLock(merchantId, () -> transactionTemplate.execute(status -> {
            String idemKey = buildIdemKey("WRITEOFF", orderNo, merchantId);
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return accountMapper.selectById(merchantId);
            }
            LscAccount acc = accountService.getOrCreateAccount(merchantId);
            return applyAccountChange(acc, idemKey, LscTransactionTypeEnum.MERCHANT_WRITE_OFF,
                    0L, -amount, null, orderNo, "商家核销销毁");
        }));
    }

    // ============================ 退款退回 ============================

    @Override
    public LscAccount refundLsc(Long userId, Long amount, String orderNo) {
        assertPositive(amount);
        // 接口约定：userId 为接收退款的消费者，本方法负责消费者可用余额入账；
        // 对应的商家可用余额扣减由退款编排流程(持有 merchantId)另行调用，保证账务平衡。
        return executeWithLock(userId, () -> transactionTemplate.execute(status -> {
            String idemKey = buildIdemKey("REFUND", orderNo, userId);
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return accountMapper.selectById(userId);
            }
            LscAccount acc = accountService.getOrCreateAccount(userId);
            writeAvailableDetail(userId, amount, LscTransactionTypeEnum.REFUND_RETURN.getDesc(), b2bValidityDays);
            return applyAccountChange(acc, idemKey, LscTransactionTypeEnum.REFUND_RETURN,
                    0L, amount, null, orderNo, "退款退回:消费者可用余额入账");
        }));
    }

    // ============================ 过期转回 ============================

    @Override
    public long expireTransfer(Long userId) {
        Long result = executeWithLock(userId, () -> transactionTemplate.execute(status -> {
            String today = LocalDate.now().toString();
            String idemKey = "EXPIRE_" + userId + "_" + today;
            if (transactionMapper.selectByIdempotentKey(idemKey) != null) {
                return 0L;
            }
            List<AvailableLscDetail> expired = detailMapper.selectExpiredForTransfer(
                    userId, LocalDate.now(), expireBatchSize);
            if (expired.isEmpty()) {
                return 0L;
            }
            long total = expired.stream().mapToLong(d -> nvl(d.getAmount())).sum();
            LscAccount acc = accountService.getOrCreateAccount(userId);
            long beforeLocked = nvl(acc.getTotalLocked());
            long beforeAvailable = nvl(acc.getTotalAvailable());
            if (beforeAvailable < total) {
                throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
            }
            acc.setTotalAvailable(beforeAvailable - total);
            acc.setTotalLocked(beforeLocked + total);
            if (accountMapper.updateById(acc) <= 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "账户更新失败(乐观锁冲突)");
            }
            // 明细状态置为已过期转回
            for (AvailableLscDetail d : expired) {
                AvailableLscDetail upd = new AvailableLscDetail();
                upd.setId(d.getId());
                upd.setStatus(AvailableLscStatusEnum.EXPIRED_TRANSFERRED.getCode());
                detailMapper.updateById(upd);
            }
            recordTransaction(userId, LscTransactionTypeEnum.EXPIRE_TRANSFER, total,
                    beforeLocked, beforeLocked + total, beforeAvailable, beforeAvailable - total,
                    null, null, idemKey, "过期转回:可用转锁定");
            return total;
        }));
        return result;
    }

    // ============================ 余额查询 ============================

    @Override
    public LscAccount getBalance(Long userId) {
        LscAccount acc = accountMapper.selectById(userId);
        if (acc == null) {
            acc = new LscAccount();
            acc.setUserId(userId);
            acc.setTotalLocked(0L);
            acc.setTotalAvailable(0L);
            acc.setVersion(0);
        }
        return acc;
    }

    // ============================ 对账聚合 ============================

    @Override
    public java.util.Map<String, Object> dailySummary(LocalDate date, List<Integer> types) {
        if (date == null) {
            date = LocalDate.now();
        }
        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<java.util.Map<String, Object>> rows = transactionMapper.aggregateByTimeRange(start, end, types);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("totalAmount", 0L);
            result.put("totalCount", 0L);
        } else {
            java.util.Map<String, Object> row = rows.get(0);
            result.put("totalAmount", toLongFromObject(row.get("totalAmount")));
            result.put("totalCount", toLongFromObject(row.get("totalCount")));
        }
        return result;
    }

    private long toLongFromObject(Object val) {
        if (val == null) {
            return 0L;
        }
        // 兼容 BigDecimal / Long / Integer / Number
        if (val instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) val).longValue();
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(String.valueOf(val));
    }

    // ============================ 释放任务支撑 ============================

    @Override
    public java.util.Map<String, Object> lockedSummary() {
        List<LscAccount> accounts = accountMapper.selectAllLockedAccounts();
        long totalLocked = 0L;
        List<java.util.Map<String, Object>> accountList = new java.util.ArrayList<>(accounts.size());
        for (LscAccount acc : accounts) {
            long locked = nvl(acc.getTotalLocked());
            if (locked <= 0) {
                continue;
            }
            totalLocked += locked;
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("userId", acc.getUserId());
            item.put("totalLocked", locked);
            accountList.add(item);
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalLocked", totalLocked);
        result.put("userCount", accounts.size());
        result.put("accounts", accountList);
        return result;
    }

    /**
     * 批量释放: 按 userId 分组后逐个用户在单次锁中完成
     * <p>
     * 相比逐条 releaseLsc 的 N 次查询 + N 次锁获取，
     * 改为按用户分组 + 单次锁 + 批量操作，大幅降低锁竞争和 DB 往返。
     * </p>
     */
    @Override
    public java.util.Map<String, Object> releaseBatch(List<com.lianshengtong.common.dto.LscLedgerOpDTO> opList) {
        int successCount = 0;
        int failedCount = 0;
        long releasedAmount = 0L;
        if (opList == null || opList.isEmpty()) {
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("total", 0);
            result.put("successCount", 0);
            result.put("failedCount", 0);
            result.put("releasedAmount", 0L);
            return result;
        }

        // 按 userId 分组
        Map<Long, List<com.lianshengtong.common.dto.LscLedgerOpDTO>> grouped = opList.stream()
                .filter(op -> op.getUserId() != null)
                .collect(Collectors.groupingBy(com.lianshengtong.common.dto.LscLedgerOpDTO::getUserId));

        for (Map.Entry<Long, List<com.lianshengtong.common.dto.LscLedgerOpDTO>> entry : grouped.entrySet()) {
            Long userId = entry.getKey();
            List<com.lianshengtong.common.dto.LscLedgerOpDTO> ops = entry.getValue();
            try {
                long userReleased = releaseUserBatch(userId, ops);
                successCount += ops.size();
                releasedAmount += userReleased;
            } catch (RuntimeException e) {
                log.warn("批量释放用户失败 userId={} err={}", userId, e.getMessage());
                failedCount += ops.size();
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", opList.size());
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("releasedAmount", releasedAmount);
        return result;
    }

    /**
     * 单用户批量释放（单次锁 + 单次事务）
     */
    private long releaseUserBatch(Long userId, List<com.lianshengtong.common.dto.LscLedgerOpDTO> ops) {
        long totalReleased = 0L;
        RLock lock;
        try {
            lock = redissonClient.getLock("lsc:ledger:lock:" + userId);
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BizException(ResultCode.TOO_MANY_REQUESTS, "用户锁获取失败 userId=" + userId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("批量释放被中断");
        }
        try {
            return transactionTemplate.execute(status -> {
                long released = 0L;
                for (com.lianshengtong.common.dto.LscLedgerOpDTO op : ops) {
                    Long amount = op.getLockedDelta() != null
                            ? Math.abs(op.getLockedDelta()) : Math.abs(op.getAvailableDelta());
                    if (amount == null || amount <= 0) {
                        continue;
                    }
                    releaseLsc(userId, amount, op.getOrderNo());
                    released += amount;
                }
                return released;
            });
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 全网过期转团单用户（批量优化版）
     * <p>
     * 相比逐条 expireTransfer 的 N 次查询 + N 次锁获取，
     * 改为一次性拉取所有过期明细 → 按用户分组 → 单用户单次锁批量处理。
     * </p>
     */
    @Override
    public java.util.Map<String, Object> expireTransferAll() {
        LocalDate today = LocalDate.now();
        int batchLimit = 2000;
        long totalTransfer = 0L;
        int userCount = 0;
        int emptyLoopGuard = 0;

        while (emptyLoopGuard < 5) {
            List<AvailableLscDetail> allExpired = detailMapper.selectBatchExpiredForTransfer(today, batchLimit);
            if (allExpired == null || allExpired.isEmpty()) {
                break;
            }
            emptyLoopGuard++;

            // 按 userId 分组
            Map<Long, List<AvailableLscDetail>> byUser = allExpired.stream()
                    .collect(Collectors.groupingBy(AvailableLscDetail::getUserId));

            for (Map.Entry<Long, List<AvailableLscDetail>> entry : byUser.entrySet()) {
                Long userId = entry.getKey();
                List<AvailableLscDetail> userDetails = entry.getValue();
                try {
                    long transferred = expireTransferUserBatch(userId, userDetails);
                    if (transferred > 0) {
                        totalTransfer += transferred;
                        userCount++;
                    }
                } catch (RuntimeException e) {
                    log.warn("全网过期转团单用户失败 userId={} err={}", userId, e.getMessage());
                }
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("userCount", userCount);
        result.put("transferAmount", totalTransfer);
        return result;
    }

    /**
     * 单用户批量过期转回（单次锁 + 单次事务 + 批量更新）
     */
    private long expireTransferUserBatch(Long userId, List<AvailableLscDetail> expired) {
        long total = expired.stream().mapToLong(d -> nvl(d.getAmount())).sum();
        if (total <= 0) {
            return 0L;
        }

        RLock lock;
        try {
            lock = redissonClient.getLock("lsc:ledger:lock:" + userId);
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("批量过期转回锁获取失败 userId={}", userId);
                return 0L;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("批量过期转回被中断 userId={}", userId);
            return 0L;
        }
        try {
            return transactionTemplate.execute(status -> {
                LscAccount acc = accountService.getOrCreateAccount(userId);
                long beforeLocked = nvl(acc.getTotalLocked());
                long beforeAvailable = nvl(acc.getTotalAvailable());
                if (beforeAvailable < total) {
                    throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
                }
                acc.setTotalAvailable(beforeAvailable - total);
                acc.setTotalLocked(beforeLocked + total);
                if (accountMapper.updateById(acc) <= 0) {
                    throw new BizException(ResultCode.SYSTEM_ERROR, "账户批量更新失败(乐观锁冲突)");
                }
                // 批量更新明细状态
                List<Long> detailIds = expired.stream()
                        .map(AvailableLscDetail::getId).collect(Collectors.toList());
                detailMapper.batchUpdateStatus(detailIds, AvailableLscStatusEnum.EXPIRED_TRANSFERRED.getCode());
                // 记录流水
                String idemKey = "EXPIRE_BATCH_" + userId + "_" + System.currentTimeMillis();
                recordTransaction(userId, LscTransactionTypeEnum.EXPIRE_TRANSFER, total,
                        beforeLocked, beforeLocked + total, beforeAvailable, beforeAvailable - total,
                        null, null, idemKey, "批量过期转回:可用转锁定");
                return total;
            });
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ============================ 商家/管理后台查询 ============================

    @Override
    public IPage<LscTransaction> transactionList(Long userId, Integer page, Integer size, Integer type,
                                                  String startDate, String endDate, String orderNo) {
        Page<LscTransaction> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<LscTransaction> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(LscTransaction::getUserId, userId);
        }
        if (type != null) {
            wrapper.eq(LscTransaction::getType, type);
        }
        if (StrUtil.isNotBlank(startDate)) {
            wrapper.ge(LscTransaction::getCreatedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StrUtil.isNotBlank(endDate)) {
            wrapper.le(LscTransaction::getCreatedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        if (StrUtil.isNotBlank(orderNo)) {
            wrapper.eq(LscTransaction::getOrderNo, orderNo);
        }
        wrapper.orderByDesc(LscTransaction::getCreatedAt);
        return transactionMapper.selectPage(p, wrapper);
    }

    @Override
    public IPage<AvailableLscDetail> availableDetails(Long userId, Integer page, Integer size, Integer status) {
        Page<AvailableLscDetail> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<AvailableLscDetail> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(AvailableLscDetail::getUserId, userId);
        }
        if (status != null) {
            wrapper.eq(AvailableLscDetail::getStatus, status);
        }
        wrapper.orderByDesc(AvailableLscDetail::getExpireDate);
        return detailMapper.selectPage(p, wrapper);
    }

    @Override
    public List<Map<String, Object>> recentTrend(Long userId, Integer days) {
        int n = days == null || days <= 0 ? 7 : days;
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            java.time.LocalDateTime start = date.atStartOfDay();
            java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
            LambdaQueryWrapper<LscTransaction> wrapper = new LambdaQueryWrapper<>();
            if (userId != null) {
                wrapper.eq(LscTransaction::getUserId, userId);
            }
            wrapper.between(LscTransaction::getCreatedAt, start, end);
            List<LscTransaction> txs = transactionMapper.selectList(wrapper);
            long lscIn = 0;
            long orderCount = 0;
            for (LscTransaction tx : txs) {
                // 收入类: 2每日释放 3推广奖励 4商城消费(商家收入) 5线下消费 8B2B流转(接收方)
                if (tx.getType() != null && (tx.getType() == 2 || tx.getType() == 3
                        || tx.getType() == 4 || tx.getType() == 5 || tx.getType() == 8)) {
                    lscIn += nvl(tx.getAmount());
                }
                if (StrUtil.isNotBlank(tx.getOrderNo())) {
                    orderCount++;
                }
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("orderCount", orderCount);
            point.put("revenue", lscIn);
            point.put("lscIn", lscIn);
            result.add(point);
        }
        return result;
    }

    @Override
    public Map<String, Object> overview(Long userId) {
        LscAccount account = getBalance(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLocked", nvl(account.getTotalLocked()));
        result.put("totalAvailable", nvl(account.getTotalAvailable()));
        // 已核销 = 流水类型7(商家核销) 累计金额
        LambdaQueryWrapper<LscTransaction> writeOffW = new LambdaQueryWrapper<>();
        if (userId != null) {
            writeOffW.eq(LscTransaction::getUserId, userId);
        }
        writeOffW.eq(LscTransaction::getType, LscTransactionTypeEnum.MERCHANT_WRITE_OFF.getCode());
        List<LscTransaction> writeOffTxs = transactionMapper.selectList(writeOffW);
        long totalWrittenOff = writeOffTxs.stream().mapToLong(t -> nvl(t.getAmount())).sum();
        result.put("totalWrittenOff", totalWrittenOff);
        result.put("totalUsed", totalWrittenOff);
        // 月收入 = 当月收入类流水累计
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LambdaQueryWrapper<LscTransaction> monthW = new LambdaQueryWrapper<>();
        if (userId != null) {
            monthW.eq(LscTransaction::getUserId, userId);
        }
        monthW.ge(LscTransaction::getCreatedAt, monthStart.atStartOfDay());
        monthW.in(LscTransaction::getType,
                LscTransactionTypeEnum.MALL_CONSUMPTION.getCode(),
                LscTransactionTypeEnum.OFFLINE_CONSUMPTION.getCode());
        List<LscTransaction> monthTxs = transactionMapper.selectList(monthW);
        long monthlyRevenue = monthTxs.stream().mapToLong(t -> nvl(t.getAmount())).sum();
        result.put("monthlyRevenue", monthlyRevenue);
        return result;
    }

    // ============================ 内部工具 ============================

    /**
     * 单账户原子变更(乐观锁 + 流水记录)
     *
     * @param acc            账户(已加载最新版本)
     * @param idemKey        幂等键
     * @param type           流水类型
     * @param lockedDelta    锁定变更量(可负)
     * @param availableDelta 可用变更量(可负)
     * @param counterpartyId 对手方用户ID
     * @param orderNo        关联订单号
     * @param remark         备注
     * @return 变更后账户
     */
    private LscAccount applyAccountChange(LscAccount acc, String idemKey, LscTransactionTypeEnum type,
                                          long lockedDelta, long availableDelta, Long counterpartyId,
                                          String orderNo, String remark) {
        long beforeLocked = nvl(acc.getTotalLocked());
        long beforeAvailable = nvl(acc.getTotalAvailable());
        long newLocked = beforeLocked + lockedDelta;
        long newAvailable = beforeAvailable + availableDelta;
        if (newLocked < 0) {
            throw new BizException(ResultCode.LSC_LOCKED_INSUFFICIENT);
        }
        if (newAvailable < 0) {
            throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
        }
        acc.setTotalLocked(newLocked);
        acc.setTotalAvailable(newAvailable);
        if (accountMapper.updateById(acc) <= 0) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "账户并发更新失败(乐观锁冲突)");
        }
        long txAmount = Math.abs(lockedDelta != 0 ? lockedDelta : availableDelta);
        recordTransaction(acc.getUserId(), type, txAmount, beforeLocked, newLocked,
                beforeAvailable, newAvailable, counterpartyId, orderNo, idemKey, remark);
        acc.setVersion(nvl(acc.getVersion()) + 1);
        return acc;
    }

    /**
     * 写入可用 LSC 明细
     */
    private void writeAvailableDetail(Long userId, Long amount, String sourceType, int validityDays) {
        AvailableLscDetail detail = new AvailableLscDetail();
        detail.setUserId(userId);
        detail.setAmount(amount);
        detail.setSourceType(sourceType);
        LocalDate expire = LocalDate.now().plusDays(validityDays);
        detail.setOriginalExpireDate(expire);
        detail.setExpireDate(expire);
        detail.setStatus(AvailableLscStatusEnum.VALID.getCode());
        detailMapper.insert(detail);
    }

    /**
     * 记录流水(含操作前后快照)，幂等键唯一索引冲突时忽略(并发重复)
     */
    private void recordTransaction(Long userId, LscTransactionTypeEnum type, Long amount,
                                   long beforeLocked, long afterLocked,
                                   long beforeAvailable, long afterAvailable,
                                   Long counterpartyId, String orderNo, String idemKey, String remark) {
        LscTransaction tx = new LscTransaction();
        tx.setUserId(userId);
        tx.setType(type.getCode());
        tx.setAmount(amount);
        tx.setBeforeLocked(beforeLocked);
        tx.setAfterLocked(afterLocked);
        tx.setBeforeAvailable(beforeAvailable);
        tx.setAfterAvailable(afterAvailable);
        tx.setCounterpartyId(counterpartyId);
        tx.setOrderNo(orderNo);
        tx.setIdempotentKey(idemKey);
        tx.setRemark(remark);
        try {
            transactionMapper.insert(tx);
        } catch (DuplicateKeyException e) {
            log.warn("[幂等冲突] 流水已存在 idemKey={}", idemKey);
        }
    }

    /**
     * 构建幂等键：有订单号时按 业务_订单号_用户 维度，无订单号时用随机生成器
     */
    private String buildIdemKey(String biz, String orderNo, Long userId) {
        if (StrUtil.isBlank(orderNo)) {
            return IdempotentKeyGenerator.generate(biz, userId);
        }
        return biz + "_" + orderNo + "_" + userId;
    }

    /**
     * 单用户分布式锁执行
     */
    private <T> T executeWithLock(Long userId, Supplier<T> action) {
        return executeWithLocks(Collections.singletonList(userId), action);
    }

    /**
     * 多用户分布式锁执行(按 userId 排序加锁，避免死锁)
     */
    private <T> T executeWithLocks(List<Long> userIds, Supplier<T> action) {
        List<Long> sorted = userIds.stream().distinct().sorted().collect(Collectors.toList());
        List<RLock> acquired = new ArrayList<>(sorted.size());
        try {
            for (Long uid : sorted) {
                RLock lock = redissonClient.getLock("lsc:ledger:lock:" + uid);
                boolean ok;
                try {
                    ok = lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("账户加锁被中断");
                }
                if (!ok) {
                    throw new BizException(ResultCode.TOO_MANY_REQUESTS, "账户操作并发，请稍后重试");
                }
                acquired.add(lock);
            }
            return action.get();
        } finally {
            for (RLock lock : acquired) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    private void assertPositive(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BizException(400, "操作数量必须为正数");
        }
    }

    private long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }


    public LscAccountMapper getAccountMapper() { return accountMapper; }
    public LscTransactionMapper getTransactionMapper() { return transactionMapper; }
    public AvailableLscDetailMapper getDetailMapper() { return detailMapper; }
    public LscAccountService getAccountService() { return accountService; }
    public RedissonClient getRedissonClient() { return redissonClient; }
    public TransactionTemplate getTransactionTemplate() { return transactionTemplate; }
    public long getLockWaitMs() { return lockWaitMs; }
    public void setLockWaitMs(long lockWaitMs) { this.lockWaitMs = lockWaitMs; }
    public long getLockLeaseMs() { return lockLeaseMs; }
    public void setLockLeaseMs(long lockLeaseMs) { this.lockLeaseMs = lockLeaseMs; }
    public int getB2bValidityDays() { return b2bValidityDays; }
    public void setB2bValidityDays(int b2bValidityDays) { this.b2bValidityDays = b2bValidityDays; }
    public boolean getOptimisticLockEnabled() { return optimisticLockEnabled; }
    public void setOptimisticLockEnabled(boolean optimisticLockEnabled) { this.optimisticLockEnabled = optimisticLockEnabled; }
    public int getExpireBatchSize() { return expireBatchSize; }
    public void setExpireBatchSize(int expireBatchSize) { this.expireBatchSize = expireBatchSize; }
}
