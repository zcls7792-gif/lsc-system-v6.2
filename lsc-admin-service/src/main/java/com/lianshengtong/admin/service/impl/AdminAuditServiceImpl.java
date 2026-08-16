package com.lianshengtong.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.entity.AdminAuditLog;
import com.lianshengtong.admin.feign.AiGatewayFeignClient;
import com.lianshengtong.admin.mapper.AdminAuditLogMapper;
import com.lianshengtong.admin.service.AdminAuditService;
import com.lianshengtong.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 管理员操作审计服务实现
 * <p>记录操作日志后异步调用 AI 网关进行异常操作监控，AI 评分 >=80 标记为异常。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    private final AdminAuditLogMapper adminAuditLogMapper;
    private final AiGatewayFeignClient aiGatewayFeignClient;

    @Override
    public void record(Long adminId, String module, String action, String targetId, String detail, String clientIp) {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setAdminId(adminId);
        auditLog.setModule(module);
        auditLog.setAction(action);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLog.setClientIp(clientIp);
        auditLog.setAiFlag(0);
        adminAuditLogMapper.insert(auditLog);
        // 异步 AI 异常监控
        asyncMonitor(auditLog, detail);
    }

    @Async
    public void asyncMonitor(AdminAuditLog auditLog, String detail) {
        try {
            R<Integer> resp = aiGatewayFeignClient.monitorAdminAction(auditLog.getAdminId(),
                    auditLog.getModule(), auditLog.getAction(), detail);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                int score = resp.getData();
                auditLog.setAiScore(score);
                auditLog.setAiFlag(score >= 80 ? 2 : (score >= 50 ? 1 : 0));
                adminAuditLogMapper.updateById(auditLog);
                if (score >= 80) {
                    log.warn("管理员异常操作告警 adminId={} module={} action={} score={}",
                            auditLog.getAdminId(), auditLog.getModule(), auditLog.getAction(), score);
                }
            }
        } catch (Exception e) {
            log.warn("AI异常监控调用失败 adminId={}", auditLog.getAdminId(), e);
        }
    }

    @Override
    public IPage<AdminAuditLog> list(Integer page, Integer size, Long adminId, Integer aiFlag) {
        Page<AdminAuditLog> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<AdminAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (adminId != null) {
            wrapper.eq(AdminAuditLog::getAdminId, adminId);
        }
        if (aiFlag != null) {
            wrapper.eq(AdminAuditLog::getAiFlag, aiFlag);
        }
        wrapper.orderByDesc(AdminAuditLog::getCreatedAt);
        return adminAuditLogMapper.selectPage(p, wrapper);
    }
}
