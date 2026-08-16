package com.lianshengtong.reconciliation.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 订单服务 Feign 客户端 - 对账时拉取当日已支付订单汇总
 * <p>支付机构侧对账基准：以订单已支付/已完成/已退款 状态的 totalPrice 聚合
 * 作为人民币支付侧金额基准(真实部署应改为对接支付机构对账文件)。</p>
 */
@FeignClient(name = "lsc-order-service", contextId = "reconciliationOrderClient")
public interface OrderFeignClient {

    /**
     * 按日期汇总订单支付金额(对账支付侧)
     *
     * @param dateStr 日期字符串(yyyy-MM-dd)
     * @return Map: {totalAmount, totalCount}
     */
    @GetMapping("/api/order/daily-summary")
    R<java.util.Map<String, Object>> dailySummary(@RequestParam("date") String dateStr);
}
