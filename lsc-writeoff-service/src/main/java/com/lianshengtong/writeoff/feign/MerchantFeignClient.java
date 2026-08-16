package com.lianshengtong.writeoff.feign;

import com.lianshengtong.common.result.R;
import com.lianshengtong.writeoff.dto.MerchantInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * 商家服务 Feign 客户端
 * <p>调用 lsc-user-service 查询商家扩展信息、更新最近核销日期。</p>
 */
@FeignClient(name = "lsc-user-service", contextId = "writeoffMerchantClient")
public interface MerchantFeignClient {

    /**
     * 查询商家扩展信息
     *
     * @param merchantId 商家ID
     * @return 商家扩展信息(含处罚状态、核销限额、最近核销日期等)
     */
    @GetMapping("/api/merchant/{merchantId}")
    R<MerchantInfoDTO> getMerchantInfo(@PathVariable("merchantId") Long merchantId);

    /**
     * 更新商家最近核销日期
     *
     * @param merchantId 商家ID
     * @param nhDate     核销日期
     * @return 操作结果
     */
    @PostMapping("/api/merchant/last-nh-date")
    R<Void> updateLastNhDate(@RequestParam("merchantId") Long merchantId,
                             @RequestParam("nhDate") LocalDate nhDate);
}
