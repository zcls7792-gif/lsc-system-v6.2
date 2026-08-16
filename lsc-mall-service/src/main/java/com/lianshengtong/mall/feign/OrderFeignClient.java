package com.lianshengtong.mall.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 订单服务 Feign 客户端 - 创建商品订单
 */
@FeignClient(name = "lsc-order-service", contextId = "mallOrderClient")
public interface OrderFeignClient {

    /**
     * 创建商城订单
     *
     * @param productId   商品ID
     * @param merchantId  商家ID
     * @param consumerId  消费者ID
     * @param lscAmount   LSC支付数量
     * @param rmbAmount   人民币补足金额
     * @param totalPrice  订单总价
     * @return 订单号
     */
    @PostMapping("/api/order/create-mall")
    R<String> createMallOrder(@RequestParam("productId") Long productId,
                              @RequestParam("merchantId") Long merchantId,
                              @RequestParam("consumerId") Long consumerId,
                              @RequestParam("lscAmount") Long lscAmount,
                              @RequestParam("rmbAmount") BigDecimal rmbAmount,
                              @RequestParam("totalPrice") BigDecimal totalPrice);
}
