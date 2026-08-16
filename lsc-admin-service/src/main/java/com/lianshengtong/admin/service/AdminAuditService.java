package com.lianshengtong.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.admin.entity.AdminAuditLog;

/**
 * 管理员操作审计服务接口
 * <p>操作日志记录 + AI异常操作监控。</p>
 */
public interface AdminAuditService {

    /**
     * 记录操作日志
     * <p>记录后异步调用 AI 网关进行异常操作监控。</p>
     *
     * @param adminId  管理员ID
     * @param module   操作模块
     * @param action   操作类型
     * @param targetId 操作目标ID
     * @param detail   操作详情(JSON)
     * @param clientIp 操作IP
     */
    void record(Long adminId, String module, String action, String targetId, String detail, String clientIp);

    /**
     * 分页查询审计日志
     *
     * @param page    页码
     * @param size    每页条数
     * @param adminId 管理员ID(可空)
     * @param aiFlag  AI异常标记(可空)
     * @return 分页结果
     */
    IPage<AdminAuditLog> list(Integer page, Integer size, Long adminId, Integer aiFlag);
}
