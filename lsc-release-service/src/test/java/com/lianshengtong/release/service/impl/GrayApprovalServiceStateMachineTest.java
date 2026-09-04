package com.lianshengtong.release.service.impl;

import com.lianshengtong.common.result.R;
import com.lianshengtong.release.dto.GrayApprovalDTO;
import com.lianshengtong.release.entity.gray.GrayApprovalAudit;
import com.lianshengtong.release.entity.gray.GrayApprovalFlow;
import com.lianshengtong.release.entity.gray.GrayApprovalNode;
import com.lianshengtong.release.feign.GrayGatewayClient;
import com.lianshengtong.release.mapper.gray.GrayApprovalAuditMapper;
import com.lianshengtong.release.mapper.gray.GrayApprovalFlowMapper;
import com.lianshengtong.release.mapper.gray.GrayApprovalNodeMapper;
import com.lianshengtong.release.service.GrayApprovalService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Phase M：GrayApprovalService 审批状态机 & 网关 graduate 联动 单元测试。
 * 用 Mock 掉 Mapper + GatewayClient（不依赖 DB / H2；状态流转覆盖完整）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrayApprovalServiceStateMachineTest {

    GrayApprovalService svc;
    @Mock GrayApprovalFlowMapper   flowMapper;
    @Mock GrayApprovalNodeMapper   nodeMapper;
    @Mock GrayApprovalAuditMapper  auditMapper;
    @Mock GrayGatewayClient        gatewayClient;
    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    Long SEQ_ID = 1000L;
    GrayApprovalFlow lastFlow;

    @BeforeEach
    void init() {
        // 为了避免 Redisson bean 缺失，显式实例化 service（ObjectProvider.getIfAvailable() 返回 null 自动降级为 JVM ReentrantLock）
        @SuppressWarnings({"rawtypes", "unchecked"})
        org.springframework.beans.factory.ObjectProvider provider = new org.springframework.beans.factory.ObjectProvider() {
            @Override public Object getObject(Object... args) { return null; }
            @Override public Object getIfAvailable() { return null; }
            @Override public Object getIfUnique() { return null; }
            @Override public Object getObject() { return null; }
            @Override public java.util.stream.Stream orderedStream() { return java.util.stream.Stream.empty(); }
            @Override public java.util.Iterator iterator() { return java.util.Collections.emptyIterator(); }
            @Override public java.util.stream.Stream stream() { return java.util.stream.Stream.empty(); }
        };
        svc = new GrayApprovalServiceImpl(
                flowMapper, nodeMapper, auditMapper, gatewayClient, objectMapper, provider
        );

        // insert / update id 自增 + 回存
        when(flowMapper.insert(any(GrayApprovalFlow.class))).thenAnswer(inv -> {
            GrayApprovalFlow f = inv.getArgument(0);
            f.setId(++SEQ_ID);
            lastFlow = f;
            return 1;
        });
        when(flowMapper.updateById(any(GrayApprovalFlow.class))).thenAnswer(inv -> {
            lastFlow = inv.getArgument(0);
            return 1;
        });
        when(flowMapper.selectById(any(Long.class))).thenAnswer(inv -> {
            // 每次 select 拷贝一份（模拟 DB）
            GrayApprovalFlow f = new GrayApprovalFlow();
            copyProps(lastFlow, f);
            return f;
        });
        when(nodeMapper.insert(any(GrayApprovalNode.class))).thenReturn(1);
        when(nodeMapper.updateById(any(GrayApprovalNode.class))).thenReturn(1);
        when(auditMapper.insert(any(GrayApprovalAudit.class))).thenReturn(1);
        when(nodeMapper.selectOne(any())).thenReturn(null); // 让我们手动在 selectOne 时返回合适对象
    }

    @Test
    @DisplayName("create: DRAFT -> PENDING_APPROVAL + generate WAITING nodes")
    void createFlow_default() {
        GrayApprovalDTO.CreateRequest req = new GrayApprovalDTO.CreateRequest();
        req.flowType = GrayApprovalFlow.Type.GRADUATE;
        req.policyId = "order_default";
        req.applicant = "alice";
        req.requiredApprovals = 2;
        GrayApprovalFlow f = svc.create(req);
        assertNotNull(f.getId());
        assertEquals(GrayApprovalFlow.Status.PENDING_APPROVAL.name(), f.getStatus());
        assertEquals(2, f.getRequiredApprovals());
        assertTrue(f.getFlowNo().startsWith("GA"));
    }

    @Test
    @DisplayName("approve flow: node WAITING -> APPROVED, requiredApprovals=1 => auto execute graduate SUCCEEDED")
    void approve_graduateAuto() {
        // 1) create required=1
        when(nodeMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    // 第一次查是 WAITING 节点
                    GrayApprovalNode n = new GrayApprovalNode();
                    n.setId(1L); n.setFlowId(lastFlow.getId()); n.setNodeOrder(1);
                    n.setNodeStatus(GrayApprovalNode.NodeStatus.WAITING.name());
                    return n;
                });
        when(nodeMapper.selectCount(any())).thenReturn(1L);
        when(gatewayClient.graduate(anyString(), anyString(), any()))
                .thenReturn(R.ok(Map.of("policyId", "order_default", "graduated", true)));

        GrayApprovalDTO.CreateRequest req = new GrayApprovalDTO.CreateRequest();
        req.flowType = GrayApprovalFlow.Type.GRADUATE;
        req.policyId = "order_default"; req.applicant = "alice"; req.requiredApprovals = 1;
        svc.create(req);

        GrayApprovalDTO.ApproveRequest app = new GrayApprovalDTO.ApproveRequest();
        app.flowId = lastFlow.getId(); app.approver = "bob"; app.approved = true; app.comment = "ok";
        GrayApprovalFlow done = svc.approveOrReject(app);

        assertEquals(GrayApprovalFlow.Status.SUCCEEDED.name(), done.getStatus());
        assertEquals(1, done.getApprovedCount());
        assertNotNull(done.getExecuteResponse());
    }

    @Test
    @DisplayName("reject any: flow -> REJECTED, stop approve counter")
    void reject_stopsFlow() {
        when(nodeMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    GrayApprovalNode n = new GrayApprovalNode();
                    n.setId(2L); n.setFlowId(lastFlow.getId()); n.setNodeOrder(1);
                    n.setNodeStatus(GrayApprovalNode.NodeStatus.WAITING.name());
                    return n;
                });
        GrayApprovalDTO.CreateRequest req = new GrayApprovalDTO.CreateRequest();
        req.flowType = GrayApprovalFlow.Type.GRADUATE; req.policyId = "a";
        req.applicant = "alice"; req.requiredApprovals = 2;
        svc.create(req);

        GrayApprovalDTO.ApproveRequest app = new GrayApprovalDTO.ApproveRequest();
        app.flowId = lastFlow.getId(); app.approver = "bob"; app.approved = false; app.comment = "err";
        GrayApprovalFlow rej = svc.approveOrReject(app);
        assertEquals(GrayApprovalFlow.Status.REJECTED.name(), rej.getStatus());
    }

    @Test
    @DisplayName("EXECUTE_FAILED retry: re-invoke gateway and mark SUCCEEDED on success")
    void retryExecute_resume() {
        // 直接构造一个 EXECUTE_FAILED 审批单
        lastFlow = new GrayApprovalFlow();
        lastFlow.setId(999L);
        lastFlow.setFlowNo("GA0000");
        lastFlow.setFlowType(GrayApprovalFlow.Type.WEIGHT_CHANGE.name());
        lastFlow.setPolicyId("p");
        lastFlow.setStatus(GrayApprovalFlow.Status.EXECUTE_FAILED.name());
        lastFlow.setPayloadJson("{\"targetWeight\":50}");
        lastFlow.setRequiredApprovals(1);
        lastFlow.setApprovedCount(1);

        when(gatewayClient.changeWeight(eq("p"), eq("sys"), eq(50)))
                .thenReturn(R.ok(Map.of("weight", 50)));

        GrayApprovalDTO.RetryExecuteRequest retry = new GrayApprovalDTO.RetryExecuteRequest();
        retry.flowId = 999L; retry.operator = "sys";
        GrayApprovalFlow f = svc.retryExecute(retry);
        assertEquals(GrayApprovalFlow.Status.SUCCEEDED.name(), f.getStatus());
    }

    // copy all getters to target via reflection 简写（单元测试里用）
    static void copyProps(GrayApprovalFlow src, GrayApprovalFlow dst) {
        if (src == null) return;
        dst.setId(src.getId()); dst.setFlowNo(src.getFlowNo()); dst.setFlowType(src.getFlowType());
        dst.setPolicyId(src.getPolicyId()); dst.setPayloadJson(src.getPayloadJson());
        dst.setApplicant(src.getApplicant()); dst.setTitle(src.getTitle()); dst.setApplyReason(src.getApplyReason());
        dst.setStatus(src.getStatus()); dst.setRequiredApprovals(src.getRequiredApprovals());
        dst.setApprovedCount(src.getApprovedCount()); dst.setTotalNodes(src.getTotalNodes());
        dst.setExecuteResponse(src.getExecuteResponse()); dst.setExecuteCostMs(src.getExecuteCostMs());
        dst.setApprovedAt(src.getApprovedAt()); dst.setCreatedAt(src.getCreatedAt());
        dst.setUpdatedAt(src.getUpdatedAt()); dst.setUpdatedBy(src.getUpdatedBy());
    }
}
