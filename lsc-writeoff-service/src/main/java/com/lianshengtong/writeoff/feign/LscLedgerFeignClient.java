package com.lianshengtong.writeoff.feign;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 账本服务 Feign 客户端
 * <p>调用 lsc-ledger-service 查询余额、执行核销销毁等原子化账务操作。</p>
 */
@FeignClient(name = "lsc-ledger-service", contextId = "writeoffLedgerClient")
public interface LscLedgerFeignClient {

    /**
     * 查询用户余额
     *
     * @param userId 用户ID
     * @return 账户快照(totalLocked/totalAvailable)
     */
    @GetMapping("/api/ledger/balance/{userId}")
    R<Map<String, Object>> getBalance(@PathVariable("userId") Long userId);

    /**
     * 商家核销销毁 LSC
     * <p>扣减商家可用 LSC 并销毁。对应 ledger-service 的 POST /api/ledger/write-off 端点。</p>
     *
     * @param opDTO 账本操作请求
     * @return 操作结果（含更新后的账户快照）
     */
    @PostMapping("/api/ledger/write-off")
    R<Map<String, Object>> writeOffLsc(@RequestBody LscLedgerOpDTO opDTO);
}
