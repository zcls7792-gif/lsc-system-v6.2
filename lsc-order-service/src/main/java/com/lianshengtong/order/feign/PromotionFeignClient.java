package com.lianshengtong.order.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 推广服务 Feign 客户端
 * <p>调用 lsc-promotion-service 通知首单等推广奖励事件。</p>
 */
@FeignClient(name = "lsc-promotion-service", contextId = "orderPromotionClient")
public interface PromotionFeignClient {

    /**
     * 通知首单完成
     * <p>订单完成时调用，由推广服务判定是否首单并触发奖励划转。
     * 失败不阻断主流程，由对账任务补偿。</p>
     *
     * @param consumerId    消费者用户ID
     * @param orderNo       订单号
     * @param orderAmount   订单实付金额(元)
     * @param orderStatus   订单状态(2=已完成)
     * @param refundAmount  累计退款金额(元，无退款传0)
     * @return 操作结果
     */
    @PostMapping("/api/promotion/first-order-notify")
    R<Void> notifyFirstOrder(@RequestParam("consumerId") Long consumerId,
                             @RequestParam("orderNo") String orderNo,
                             @RequestParam("orderAmount") BigDecimal orderAmount,
                             @RequestParam("orderStatus") Integer orderStatus,
                             @RequestParam(value = "refundAmount", required = false) BigDecimal refundAmount);
}
