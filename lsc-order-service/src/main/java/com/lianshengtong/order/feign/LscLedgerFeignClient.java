package com.lianshengtong.order.feign;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 账本服务 Feign 客户端
 * <p>调用 lsc-ledger-service 执行发行、支付、退款退回等原子化账务操作。</p>
 */
@FeignClient(name = "lsc-ledger-service", contextId = "orderLedgerClient")
public interface LscLedgerFeignClient {

    /**
     * 消费发行 LSC(锁定)
     * <p>消费者下单后，按订单金额发行等量 LSC 到锁定余额。</p>
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/issue")
    R<Void> issueLsc(@RequestBody LscLedgerOpDTO opDTO);

    /**
     * 消费支付 LSC(消费者可用余额扣减并转入商家可用余额)
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/pay")
    R<Void> payLsc(@RequestBody LscLedgerOpDTO opDTO);

    /**
     * 退款退回 LSC(商家可用 LSC 退回消费者可用余额，并触发发行回滚)
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/refund")
    R<Void> refundLsc(@RequestBody LscLedgerOpDTO opDTO);
}
