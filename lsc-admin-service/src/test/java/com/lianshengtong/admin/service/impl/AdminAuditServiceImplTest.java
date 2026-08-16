package com.lianshengtong.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.entity.AdminAuditLog;
import com.lianshengtong.admin.feign.AiGatewayFeignClient;
import com.lianshengtong.admin.mapper.AdminAuditLogMapper;
import com.lianshengtong.common.result.R;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("管理员审计服务单元测试")
class AdminAuditServiceImplTest {

    @Mock
    private AdminAuditLogMapper adminAuditLogMapper;
    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;

    @InjectMocks
    private AdminAuditServiceImpl adminAuditService;

    // ============== record 测试 ==============

    @Test
    @DisplayName("record: 正常记录审计日志")
    void record_normalCase() {
        adminAuditService.record(1L, "user", "login", "target-001", "{\"key\":\"val\"}", "192.168.1.1");
        verify(adminAuditLogMapper).insert(any(AdminAuditLog.class));
    }

    @Test
    @DisplayName("record: 审计日志字段正确")
    void record_fieldsSetCorrectly() {
        adminAuditService.record(2L, "product", "update", "p-100", "detail-info", "10.0.0.1");
        verify(adminAuditLogMapper).insert(argThat(log ->
                log.getAdminId().equals(2L)
                        && "product".equals(log.getModule())
                        && "update".equals(log.getAction())
                        && "p-100".equals(log.getTargetId())
                        && "detail-info".equals(log.getDetail())
                        && "10.0.0.1".equals(log.getClientIp())
                        && log.getAiFlag() == 0
        ));
    }

    @Test
    @DisplayName("record: 所有管理员均可记录")
    void record_multipleAdmins() {
        for (long adminId : new long[]{1L, 50L, 999L}) {
            adminAuditService.record(adminId, "user", "login", "t", "d", "ip");
        }
        verify(adminAuditLogMapper, times(3)).insert(any(AdminAuditLog.class));
    }

    @Test
    @DisplayName("record: 不同模块和操作类型")
    void record_differentModules() {
        adminAuditService.record(1L, "b2b", "audit", "t1", "d1", "ip1");
        adminAuditService.record(1L, "risk", "punish", "t2", "d2", "ip2");
        adminAuditService.record(1L, "evidence", "save", "t3", "d3", "ip3");
        verify(adminAuditLogMapper, times(3)).insert(any(AdminAuditLog.class));
    }

    // ============== asyncMonitor 测试 ==============

    @Test
    @DisplayName("asyncMonitor: AI评分<50标记为正常")
    void asyncMonitor_scoreBelow50_markNormal() {
        AdminAuditLog log = buildAuditLog(1L, "user", "login", "detail");
        R<Integer> resp = R.ok(30);
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper).updateById(argThat(l ->
                l.getAiScore() == 30 && l.getAiFlag() == 0
        ));
    }

    @Test
    @DisplayName("asyncMonitor: AI评分50-79标记为可疑")
    void asyncMonitor_score50to79_markSuspicious() {
        AdminAuditLog log = buildAuditLog(2L, "product", "update", "detail");
        R<Integer> resp = R.ok(65);
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper).updateById(argThat(l ->
                l.getAiScore() == 65 && l.getAiFlag() == 1
        ));
    }

    @Test
    @DisplayName("asyncMonitor: AI评分>=80标记为异常")
    void asyncMonitor_scoreAbove80_markAbnormal() {
        AdminAuditLog log = buildAuditLog(3L, "b2b", "config", "detail");
        R<Integer> resp = R.ok(95);
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper).updateById(argThat(l ->
                l.getAiScore() == 95 && l.getAiFlag() == 2
        ));
    }

    @Test
    @DisplayName("asyncMonitor: AI评分恰好80标记为异常")
    void asyncMonitor_scoreExactly80_markAbnormal() {
        AdminAuditLog log = buildAuditLog(4L, "risk", "punish", "detail");
        R<Integer> resp = R.ok(80);
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper).updateById(argThat(l ->
                l.getAiScore() == 80 && l.getAiFlag() == 2
        ));
    }

    @Test
    @DisplayName("asyncMonitor: AI评分恰好50标记为可疑")
    void asyncMonitor_scoreExactly50_markSuspicious() {
        AdminAuditLog log = buildAuditLog(5L, "evidence", "save", "detail");
        R<Integer> resp = R.ok(50);
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper).updateById(argThat(l ->
                l.getAiScore() == 50 && l.getAiFlag() == 1
        ));
    }

    @Test
    @DisplayName("asyncMonitor: AI网关返回null不更新")
    void asyncMonitor_aiRespNull_noUpdate() {
        AdminAuditLog log = buildAuditLog(6L, "user", "login", "detail");
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(null);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper, never()).updateById(any(AdminAuditLog.class));
    }

    @Test
    @DisplayName("asyncMonitor: AI网关返回成功但data为null不更新")
    void asyncMonitor_aiRespSuccessNullData_noUpdate() {
        AdminAuditLog log = buildAuditLog(7L, "product", "update", "detail");
        R<Integer> resp = R.ok();
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper, never()).updateById(any(AdminAuditLog.class));
    }

    @Test
    @DisplayName("asyncMonitor: AI网关返回失败不更新")
    void asyncMonitor_aiRespFail_noUpdate() {
        AdminAuditLog log = buildAuditLog(8L, "b2b", "audit", "detail");
        R<Integer> resp = R.fail("error");
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper, never()).updateById(any(AdminAuditLog.class));
    }

    @Test
    @DisplayName("asyncMonitor: AI网关抛异常不中断")
    void asyncMonitor_aiRespThrows_noPropagation() {
        AdminAuditLog log = buildAuditLog(9L, "risk", "punish", "detail");
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() -> adminAuditService.asyncMonitor(log, "detail"));
    }

    @Test
    @DisplayName("asyncMonitor: AI评分0标记为正常")
    void asyncMonitor_scoreZero_markNormal() {
        AdminAuditLog log = buildAuditLog(10L, "evidence", "save", "detail");
        R<Integer> resp = R.ok(0);
        when(aiGatewayFeignClient.monitorAdminAction(anyLong(), anyString(), anyString(), anyString())).thenReturn(resp);

        adminAuditService.asyncMonitor(log, "detail");

        verify(adminAuditLogMapper).updateById(argThat(l ->
                l.getAiScore() == 0 && l.getAiFlag() == 0
        ));
    }

    // ============== list 测试 ==============

    @Test
    @DisplayName("list: 默认参数查询")
    void list_defaultParams() {
        IPage<AdminAuditLog> page = new Page<>(1, 20);
        doReturn(page).when(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        IPage<AdminAuditLog> result = adminAuditService.list(null, null, null, null);

        assertNotNull(result);
        verify(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 自定义分页参数")
    void list_customPageSize() {
        IPage<AdminAuditLog> page = new Page<>(3, 50);
        doReturn(page).when(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        adminAuditService.list(3, 50, null, null);

        verify(adminAuditLogMapper).selectPage(argThat(p ->
                p.getCurrent() == 3 && p.getSize() == 50
        ), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 按管理员ID筛选")
    void list_filterByAdminId() {
        IPage<AdminAuditLog> page = new Page<>(1, 20);
        doReturn(page).when(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        adminAuditService.list(1, 20, 100L, null);

        verify(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 按AI标记筛选")
    void list_filterByAiFlag() {
        IPage<AdminAuditLog> page = new Page<>(1, 20);
        doReturn(page).when(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        adminAuditService.list(1, 20, null, 2);

        verify(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 同时按管理员ID和AI标记筛选")
    void list_filterByBoth() {
        IPage<AdminAuditLog> page = new Page<>(1, 20);
        doReturn(page).when(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        adminAuditService.list(1, 20, 200L, 1);

        verify(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 返回空结果集")
    void list_emptyResult() {
        Page<AdminAuditLog> emptyPage = new Page<>(1, 20);
        doReturn(emptyPage).when(adminAuditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        IPage<AdminAuditLog> result = adminAuditService.list(1, 20, 999L, 2);

        assertNotNull(result);
        assertEquals(0, result.getRecords().size());
    }

    // ============== 辅助方法 ==============

    private AdminAuditLog buildAuditLog(Long adminId, String module, String action, String detail) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(adminId);
        log.setModule(module);
        log.setAction(action);
        log.setDetail(detail);
        return log;
    }
}
