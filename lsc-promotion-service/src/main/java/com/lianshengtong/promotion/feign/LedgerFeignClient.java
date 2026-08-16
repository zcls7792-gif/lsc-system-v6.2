package com.lianshengtong.promotion.feign;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 账本服务 Feign 客户端
 * <p>
 * 用于推广奖励划转：从推荐人锁定池划转至可用池(PROMOTION_REWARD)，
 * 及首单全额退款时的奖励回滚。严格限定一级推荐关系。
 * </p>
 */
@FeignClient(name = "lsc-ledger-service", contextId = "promotionLedgerClient")
public interface LedgerFeignClient {

    /**
     * 通用账本操作接口
     * <p>奖励划转时传入 transactionType=3(PROMOTION_REWARD)、
     * lockedDelta=-奖励(推荐人锁定扣减)、availableDelta=+奖励(推荐人可用增加)。</p>
     */
    @PostMapping("/api/ledger/op")
    R<Object> ledgerOp(@RequestBody LscLedgerOpDTO dto);
}
