package com.lianshengtong.promotion.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 用户服务 Feign 客户端 - 反查用户信息(用于获取推荐人ID)
 */
@FeignClient(name = "lsc-user-service", contextId = "promotionUserClient")
public interface UserFeignClient {

    /**
     * 查询用户信息(仅需 referrerId / isVerified 等字段)
     *
     * @param userId 用户ID
     * @return 用户信息(由 user-service 返回 User 序列化结果)
     */
    @GetMapping("/api/user/info")
    R<Map<String, Object>> getUserInfo(@RequestParam("userId") Long userId);
}
