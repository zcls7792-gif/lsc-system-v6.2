package com.lianshengtong.order.feign;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 账本服务 Feign 客户端 (V7.3)
 * <p>调用 lsc-ledger-service 执行原子化账务操作。</p>
 * <p>V7.3 变更：移除 payLsc(V6.2 消费者→商家流转)，改用 deductLsc(LSC 销毁抵扣)；
 * 新增 refundDeductLsc(退款时扣回赠送的锁定 LSC)。</p>
 */
@FeignClient(name = "lsc-ledger-service", contextId = "orderLedgerClient")
public interface LscLedgerFeignClient {

    /**
     * 消费赠送 LSC(入锁定)
     * <p>消费者下单支付后，按订单人民币实付部分计算赠送 LSC，进入消费者锁定池。</p>
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/issue")
    R<Void> issueLsc(@RequestBody LscLedgerOpDTO opDTO);

    /**
     * 订单抵扣 LSC(消费者可用余额销毁)
     * <p>V7.3：消费者下单时使用可用 LSC 抵扣订单金额，LSC 直接销毁(不流转给商家)。</p>
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/deduct")
    R<Void> deductLsc(@RequestBody LscLedgerOpDTO opDTO);

    /**
     * 退款退回 LSC(消费者可用余额入账)
     * <p>退款时将原订单抵扣的 LSC 退回消费者可用余额。</p>
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/refund")
    R<Void> refundLsc(@RequestBody LscLedgerOpDTO opDTO);

    /**
     * 退款回扣 LSC(扣回订单获赠的锁定 LSC)
     * <p>订单退款时，将该订单已赠送的 LSC 从消费者锁定池中扣回(销毁)，与 refundLsc 配对。</p>
     *
     * @param opDTO 账本操作请求
     * @return 操作结果
     */
    @PostMapping("/api/ledger/refund-deduct")
    R<Void> refundDeductLsc(@RequestBody LscLedgerOpDTO opDTO);
}
