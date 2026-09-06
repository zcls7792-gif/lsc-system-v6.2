package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.LscAccount;
import com.lianshengtong.lsc.entity.LscTransaction;
import com.lianshengtong.lsc.entity.SysUser;
import com.lianshengtong.lsc.mapper.LscAccountMapper;
import com.lianshengtong.lsc.mapper.LscTransactionMapper;
import com.lianshengtong.lsc.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LscAccountService {

    private final LscAccountMapper lscAccountMapper;
    private final LscTransactionMapper txMapper;
    private final SysUserMapper sysUserMapper;

    public Map<String, Object> getAccount(Long userId) {
        LscAccount acc = lscAccountMapper.selectOne(
                new LambdaQueryWrapper<LscAccount>().eq(LscAccount::getUserId, userId));
        if (acc == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        // 今日释放（模拟：取昨日流水 type=2）
        Long todayReleased = 0L;
        try {
            todayReleased = txMapper.selectList(
                    new LambdaQueryWrapper<LscTransaction>()
                            .eq(LscTransaction::getUserId, userId)
                            .eq(LscTransaction::getType, 2)
                            .ge(LscTransaction::getCreatedAt, LocalDateTime.now().toLocalDate().atStartOfDay())
            ).stream().mapToLong(LscTransaction::getAmount).sum();
        } catch (Exception ignored) {}

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("totalLocked", acc.getTotalLocked());
        result.put("totalAvailable", acc.getTotalAvailable());
        result.put("todayReleased", todayReleased);
        result.put("releaseRate", "0.045%");
        result.put("expiringCount", 0);
        return result;
    }

    public Page<LscTransaction> getTransactions(Long userId, Integer type, int pageNo, int pageSize) {
        LambdaQueryWrapper<LscTransaction> qw = new LambdaQueryWrapper<LscTransaction>()
                .eq(LscTransaction::getUserId, userId)
                .orderByDesc(LscTransaction::getCreatedAt);
        if (type != null) qw.eq(LscTransaction::getType, type);
        return txMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /**
     * LSC 流转核心方法
     * @param fromUserId 转出方
     * @param toUserId 转入方（null 表示销毁，如核销）
     * @param amount 数量
     * @param type 流水类型
     * @param orderNo 订单号
     */
    @Transactional
    public void transfer(Long fromUserId, Long toUserId, Long amount, int type, String orderNo) {
        LscAccount from = lscAccountMapper.selectOne(
                new LambdaQueryWrapper<LscAccount>().eq(LscAccount::getUserId, fromUserId));
        if (from == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (from.getTotalAvailable() < amount) {
            throw new BusinessException(ErrorCode.LSC_INSUFFICIENT);
        }

        // 转出方流水
        LscTransaction outTx = new LscTransaction();
        outTx.setUserId(fromUserId);
        outTx.setType(type);
        outTx.setAmount(-amount);
        outTx.setBeforeAvailable(from.getTotalAvailable());
        outTx.setAfterAvailable(from.getTotalAvailable() - amount);
        outTx.setBeforeLocked(from.getTotalLocked());
        outTx.setAfterLocked(from.getTotalLocked());
        outTx.setCounterpartyId(toUserId);
        outTx.setOrderNo(orderNo);
        outTx.setCreatedAt(LocalDateTime.now());
        txMapper.insert(outTx);

        from.setTotalAvailable(from.getTotalAvailable() - amount);
        from.setUpdatedAt(LocalDateTime.now());
        lscAccountMapper.updateById(from);

        // 转入方（核销时 toUserId=null，LSC 销毁）
        if (toUserId != null) {
            LscAccount to = lscAccountMapper.selectOne(
                    new LambdaQueryWrapper<LscAccount>().eq(LscAccount::getUserId, toUserId));
            if (to == null) {
                to = new LscAccount();
                to.setUserId(toUserId);
                to.setTotalLocked(0L);
                to.setTotalAvailable(0L);
                lscAccountMapper.insert(to);
            }
            LscTransaction inTx = new LscTransaction();
            inTx.setUserId(toUserId);
            inTx.setType(type);
            inTx.setAmount(amount);
            inTx.setBeforeAvailable(to.getTotalAvailable());
            inTx.setAfterAvailable(to.getTotalAvailable() + amount);
            inTx.setBeforeLocked(to.getTotalLocked());
            inTx.setAfterLocked(to.getTotalLocked());
            inTx.setCounterpartyId(fromUserId);
            inTx.setOrderNo(orderNo);
            inTx.setCreatedAt(LocalDateTime.now());
            txMapper.insert(inTx);

            to.setTotalAvailable(to.getTotalAvailable() + amount);
            to.setUpdatedAt(LocalDateTime.now());
            lscAccountMapper.updateById(to);
        }
    }

    /**
     * 校验流转权限矩阵
     * 消→消：禁止；消→商：允许（消费）；商→消：禁止；商→商：允许（B2B）；核销：仅商家
     */
    public void checkFlowPermission(Long fromUserId, Long toUserId) {
        SysUser from = sysUserMapper.selectById(fromUserId);
        if (toUserId != null) {
            SysUser to = sysUserMapper.selectById(toUserId);
            if (from.getUserType() == 0 && to.getUserType() == 0) {
                throw new BusinessException(ErrorCode.LSC_FLOW_FORBIDDEN); // 消→消
            }
            if (from.getUserType() == 1 && to.getUserType() == 0) {
                throw new BusinessException(ErrorCode.LSC_FLOW_FORBIDDEN); // 商→消
            }
        }
    }
}
