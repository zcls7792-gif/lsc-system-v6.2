package com.lianshengtong.b2b.feign;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 账本服务 Feign 客户端
 * <p>调用 lsc-ledger-service 执行 B2B 1:1 流转等原子化账务操作。</p>
 */
@FeignClient(name = "lsc-ledger-service", contextId = "b2bLedgerClient")
public interface LscLedgerFeignClient {

    /**
     * B2B 流转(商家 -&gt; 商家)
     * <p>扣减发起方可用 LSC，增加接收方可用 LSC，接收方有效期重置365天。</p>
     *
     * @param opDTO 账本操作请求(需包含 idempotentKey/fromUserId/toUserId/amount/orderNo)
     * @return 操作结果
     */
    @PostMapping("/api/ledger/b2b-transfer")
    R<Void> b2bTransfer(@RequestBody LscLedgerOpDTO opDTO);
}
