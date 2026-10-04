package com.lianshengtong.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.mapper.LscAccountMapper;
import com.lianshengtong.ledger.service.LscLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期作废定时任务（V7.7.2 第五章 5.5）
 * 到期按批次余额分别处理，冻结不暂停有效期。
 * 账务支付校验实时检查有效期，不能依赖过期任务是否及时运行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireTask {

    private final LscLedgerService ledgerService;
    private final LscAccountMapper accountMapper;

    @Scheduled(cron = "0 0 1 * * ?")
    public void executeExpire() {
        LocalDateTime now = LocalDateTime.now();
        log.info("[过期作废] 开始执行，now={}", now);

        long lastUserId = 0L;
        int batchSize = 1000;
        int totalUsers = 0;

        while (true) {
            List<LscAccount> accounts = accountMapper.selectList(
                    new LambdaQueryWrapper<LscAccount>()
                            .gt(LscAccount::getUserId, lastUserId)
                            .gt(LscAccount::getAvailableUnit, 0)
                            .orderByAsc(LscAccount::getUserId)
                            .last("LIMIT " + batchSize));
            if (accounts.isEmpty()) break;

            for (LscAccount acc : accounts) {
                try {
                    ledgerService.expireAvailableLots(acc.getUserId(), now);
                    totalUsers++;
                } catch (Exception e) {
                    log.error("[过期作废] 用户{}失败", acc.getUserId(), e);
                }
                lastUserId = acc.getUserId();
            }
        }
        log.info("[过期作废] 完成，共处理 {} 个用户", totalUsers);
    }
}
