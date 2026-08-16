package com.lianshengtong.user.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 风控服务 Feign 客户端
 * <p>
 * 通过 Nacos 服务名 {@code lsc-risk-service} 路由到风控服务，
 * 用于在管理后台商家管理页面聚合展示风控日志。
 * </p>
 *
 * @author lsc
 */
@FeignClient(name = "lsc-risk-service", contextId = "riskFeignClient")
public interface RiskFeignClient {

    /**
     * 风控日志列表（分页）
     * <p>
     * 对应后端 {@code GET /api/risk/logs}，返回 MyBatis-Plus 的 IPage 序列化结构：
     * {@code { records, total, current, size, pages }}。
     * Feign 反序列化为 {@code Map<String, Object>}，调用方按需读取字段。
     * </p>
     *
     * @param page         页码
     * @param size         每页条数
     * @param userId       用户ID（可空）
     * @param riskLevel    风险等级 1低 2中 3高（可空）
     * @param handleStatus 处理状态 0待处理 1已自动限制 2已推送人工 3已忽略 4已解封（可空）
     * @return 包含分页数据的 R 包装
     */
    @GetMapping("/api/risk/logs")
    R<Map<String, Object>> logs(@RequestParam("page") Integer page,
                                @RequestParam("size") Integer size,
                                @RequestParam(value = "userId", required = false) Long userId,
                                @RequestParam(value = "riskLevel", required = false) Integer riskLevel,
                                @RequestParam(value = "handleStatus", required = false) Integer handleStatus);
}
