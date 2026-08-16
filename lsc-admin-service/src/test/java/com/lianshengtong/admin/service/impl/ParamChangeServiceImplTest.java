package com.lianshengtong.admin.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.config.ConfigCenterAccessor;
import com.lianshengtong.admin.entity.ParamChangeApproval;
import com.lianshengtong.admin.feign.EvidenceFeignClient;
import com.lianshengtong.admin.mapper.ParamChangeApprovalMapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("参数变更审批服务单元测试")
class ParamChangeServiceImplTest {

    @Mock
    private ParamChangeApprovalMapper paramChangeApprovalMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private EvidenceFeignClient evidenceFeignClient;
    @Mock
    private ConfigCenterAccessor configCenterAccessor;

    @InjectMocks
    private ParamChangeServiceImpl paramChangeService;

    private RLock lock;

    @BeforeEach
    void setUp() throws Exception {
        setField(paramChangeService, "requiredSignatures", 2);
        lock = mock(RLock.class);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ============== submit 测试 ==============

    @Test
    @DisplayName("submit: 正常提交审批")
    void submit_normalCase() {
        when(configCenterAccessor.getOriginalValue("rate_max")).thenReturn("0.85");
        when(paramChangeApprovalMapper.insert(any(ParamChangeApproval.class))).thenAnswer(inv -> {
            ParamChangeApproval p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });

        ParamChangeApproval result = paramChangeService.submit("rate_max", "0.95", 100L, "调高上限");

        assertNotNull(result);
        assertEquals("rate_max", result.getConfigKey());
        assertEquals("0.85", result.getOldValue());
        assertEquals("0.95", result.getNewValue());
        assertEquals(100L, result.getInitiatorId());
        assertEquals(0, result.getStatus());
        assertEquals("[]", result.getApproverSignatures());
        assertEquals("[]", result.getSignedAdminIds());
        assertEquals("调高上限", result.getRemark());
    }

    @Test
    @DisplayName("submit: 原值为null时设为空字符串")
    void submit_nullOldValue_emptyString() {
        when(configCenterAccessor.getOriginalValue("new_key")).thenReturn(null);
        when(paramChangeApprovalMapper.insert(any(ParamChangeApproval.class))).thenReturn(1);

        ParamChangeApproval result = paramChangeService.submit("new_key", "val", 200L, "新配置");

        assertEquals("", result.getOldValue());
    }

    @Test
    @DisplayName("submit: 原值为空字符串时保持空")
    void submit_emptyOldValue_kept() {
        when(configCenterAccessor.getOriginalValue("key")).thenReturn("");
        when(paramChangeApprovalMapper.insert(any(ParamChangeApproval.class))).thenReturn(1);

        ParamChangeApproval result = paramChangeService.submit("key", "val", 300L, "");

        assertEquals("", result.getOldValue());
    }

    @Test
    @DisplayName("submit: 不同发起人多次提交")
    void submit_multipleInitiators() {
        when(configCenterAccessor.getOriginalValue(anyString())).thenReturn("old");
        when(paramChangeApprovalMapper.insert(any(ParamChangeApproval.class))).thenReturn(1);

        paramChangeService.submit("k1", "v1", 1L, "r1");
        paramChangeService.submit("k2", "v2", 2L, "r2");
        paramChangeService.submit("k3", "v3", 3L, "r3");

        verify(paramChangeApprovalMapper, times(3)).insert(any(ParamChangeApproval.class));
    }

    // ============== approve 测试 ==============

    @Test
    @DisplayName("approve: 第一次签名成功")
    void approve_firstSignature() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "rate_max", "0.85", "0.95", 100L);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        ParamChangeApproval result = paramChangeService.approve(1L, 200L, "sig-admin200");

        assertNotNull(result);
        assertTrue(result.getSignedAdminIds().contains("200"));
        assertTrue(result.getApproverSignatures().contains("sig-admin200"));
        assertEquals(0, result.getStatus());
    }

    @Test
    @DisplayName("approve: 第二次签名达到阈值状态变更为通过")
    void approve_secondSignature_statusChanged() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "rate_max", "0.85", "0.95", 100L);
        approval.setSignedAdminIds("[200]");
        approval.setApproverSignatures("[\"sig-admin200\"]");
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.ok("tx-hash-001"));

        ParamChangeApproval result = paramChangeService.approve(1L, 300L, "sig-admin300");

        assertEquals(1, result.getStatus());
        assertNotNull(result.getEvidenceTxHash());
        assertEquals("tx-hash-001", result.getEvidenceTxHash());
    }

    @Test
    @DisplayName("approve: 并发锁获取失败抛异常")
    void approve_lockFail_throws() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThrows(BizException.class, () -> paramChangeService.approve(1L, 200L, "sig"));
    }

    @Test
    @DisplayName("approve: 审批单不存在抛异常")
    void approve_notFound_throws() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> paramChangeService.approve(999L, 200L, "sig"));
    }

    @Test
    @DisplayName("approve: 非待审批状态抛异常")
    void approve_alreadyProcessed_throws() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setStatus(1);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

        assertThrows(BizException.class, () -> paramChangeService.approve(1L, 200L, "sig"));
    }

    @Test
    @DisplayName("approve: 同一管理员重复签名抛异常")
    void approve_duplicateSigner_throws() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setSignedAdminIds("[200]");
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

        assertThrows(BizException.class, () -> paramChangeService.approve(1L, 200L, "sig-again"));
    }

    @Test
    @DisplayName("approve: 发起人自己签名抛异常")
    void approve_initiatorSign_throws() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

        assertThrows(BizException.class, () -> paramChangeService.approve(1L, 100L, "sig"));
    }

    @Test
    @DisplayName("approve: 存证成功后状态为1")
    void approve_evidenceSuccess_status1() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setSignedAdminIds("[200]");
        approval.setApproverSignatures("[\"s1\"]");
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.ok("tx-hash-123"));

        ParamChangeApproval result = paramChangeService.approve(1L, 300L, "s2");

        assertEquals(1, result.getStatus());
        assertEquals("tx-hash-123", result.getEvidenceTxHash());
    }

    @Test
    @DisplayName("approve: 存证失败不影响状态推进")
    void approve_evidenceFail_statusStill1() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setSignedAdminIds("[200]");
        approval.setApproverSignatures("[\"s1\"]");
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.fail("存证失败"));

        ParamChangeApproval result = paramChangeService.approve(1L, 300L, "s2");

        assertEquals(1, result.getStatus());
        assertNull(result.getEvidenceTxHash());
    }

    @Test
    @DisplayName("approve: 存证调用抛异常不影响状态推进")
    void approve_evidenceThrows_statusStill1() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setSignedAdminIds("[200]");
        approval.setApproverSignatures("[\"s1\"]");
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("存证服务不可用"));

        ParamChangeApproval result = paramChangeService.approve(1L, 300L, "s2");

        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("approve: 已签名列表为null时正确处理")
    void approve_nullSignedIds_handled() throws Exception {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setSignedAdminIds(null);
        approval.setApproverSignatures(null);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);

        ParamChangeApproval result = paramChangeService.approve(1L, 200L, "sig1");

        assertEquals(0, result.getStatus());
        assertEquals("[200]", result.getSignedAdminIds());
    }

    // ============== reject 测试 ==============

    @Test
    @DisplayName("reject: 正常拒绝")
    void reject_normalCase() {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
        when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);

        paramChangeService.reject(1L, 200L, "风险过高");

        verify(paramChangeApprovalMapper).updateById(argThat(p ->
                p.getStatus() == 2
                        && p.getRemark().contains("200")
                        && p.getRemark().contains("风险过高")
        ));
    }

    @Test
    @DisplayName("reject: 审批单不存在抛异常")
    void reject_notFound_throws() {
        when(paramChangeApprovalMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> paramChangeService.reject(999L, 200L, "reason"));
    }

    @Test
    @DisplayName("reject: 非待审批状态抛异常")
    void reject_alreadyProcessed_throws() {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setStatus(1);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

        assertThrows(BizException.class, () -> paramChangeService.reject(1L, 200L, "reason"));
    }

    @Test
    @DisplayName("reject: 已拒绝的审批单不能再拒绝")
    void reject_alreadyRejected_throws() {
        ParamChangeApproval approval = buildApproval(1L, "key", "old", "new", 100L);
        approval.setStatus(2);
        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

        assertThrows(BizException.class, () -> paramChangeService.reject(1L, 200L, "reason"));
    }

    // ============== list 测试 ==============

    @Test
    @DisplayName("list: 默认参数查询")
    void list_defaultParams() {
        IPage<ParamChangeApproval> page = new Page<>(1, 20);
        doReturn(page).when(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        IPage<ParamChangeApproval> result = paramChangeService.list(null, null, null);

        assertNotNull(result);
        verify(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 自定义分页参数")
    void list_customPageSize() {
        IPage<ParamChangeApproval> page = new Page<>(2, 30);
        doReturn(page).when(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        paramChangeService.list(2, 30, null);

        verify(paramChangeApprovalMapper).selectPage(argThat(p ->
                p.getCurrent() == 2 && p.getSize() == 30
        ), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 按状态筛选")
    void list_filterByStatus() {
        IPage<ParamChangeApproval> page = new Page<>(1, 20);
        doReturn(page).when(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        paramChangeService.list(1, 20, 0);

        verify(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 已通过状态筛选")
    void list_filterByApproved() {
        IPage<ParamChangeApproval> page = new Page<>(1, 20);
        doReturn(page).when(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        paramChangeService.list(1, 20, 1);

        verify(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list: 返回空结果集")
    void list_emptyResult() {
        Page<ParamChangeApproval> emptyPage = new Page<>(1, 20);
        doReturn(emptyPage).when(paramChangeApprovalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        IPage<ParamChangeApproval> result = paramChangeService.list(1, 20, 99);

        assertNotNull(result);
        assertEquals(0, result.getRecords().size());
    }

    // ============== 辅助方法 ==============

    private ParamChangeApproval buildApproval(Long id, String configKey, String oldValue,
                                                String newValue, Long initiatorId) {
        ParamChangeApproval approval = new ParamChangeApproval();
        approval.setId(id);
        approval.setConfigKey(configKey);
        approval.setOldValue(oldValue);
        approval.setNewValue(newValue);
        approval.setInitiatorId(initiatorId);
        approval.setStatus(0);
        approval.setApproverSignatures("[]");
        approval.setSignedAdminIds("[]");
        return approval;
    }
}
