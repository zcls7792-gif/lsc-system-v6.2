package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiAddressVerifyDTO;

/**
 * 地址真实性核验服务
 * <p>
 * 对比高德/百度地图实景图、工商注册地址，核验经营地址真实性。
 * 调用外部AI模型API，超时10秒自动降级为人工审核模式。
 * </p>
 */
public interface AiAddressVerifyService {

    /**
     * 地址核验
     *
     * @param request 核验请求(地址信息、经纬度)
     * @return 核验响应(地址一致性、经纬度匹配度、实景图比对)
     */
    AiAddressVerifyDTO.Response verify(AiAddressVerifyDTO.Request request);
}
