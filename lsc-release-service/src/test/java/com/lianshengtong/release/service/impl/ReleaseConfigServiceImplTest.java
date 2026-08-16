package com.lianshengtong.release.service.impl;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.release.entity.ParamChangeApproval;
import com.lianshengtong.release.entity.ReleaseConfig;
import com.lianshengtong.release.mapper.ParamChangeApprovalMapper;
import com.lianshengtong.release.mapper.ReleaseConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseConfigServiceImpl 单元测试")
class ReleaseConfigServiceImplTest {

    @Mock
    private ReleaseConfigMapper releaseConfigMapper;
    @Mock
    private ParamChangeApprovalMapper paramChangeApprovalMapper;

    @InjectMocks
    private ReleaseConfigServiceImpl releaseConfigService;

    private Map<String, ReleaseConfig> cache;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        Map<String, ReleaseConfig> cacheRef = (Map<String, ReleaseConfig>) ReflectionTestUtils.getField(releaseConfigService, "cache");
        cache = cacheRef;
        cache.clear();
    }

    private ReleaseConfig createConfig(String key, String value, int editable) {
        ReleaseConfig config = new ReleaseConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setEditable(editable);
        config.setDescription("Test config for " + key);
        return config;
    }

    private void putInCache(ReleaseConfig config) {
        cache.put(config.getConfigKey(), config);
    }

    // ============== getRateMax / getValue 测试 ==============

    @Test
    @DisplayName("getRateMax: 缓存有值时直接返回")
    void getRateMax_returnsValue() {
        putInCache(createConfig(ReleaseConfigServiceImpl.KEY_RATE_MAX, "0.0005", 0));

        BigDecimal result = releaseConfigService.getRateMax();

        assertEquals(0, new BigDecimal("0.0005").compareTo(result));
    }

    @Test
    @DisplayName("getRateMax: 缓存值为 null 时返回默认值")
    void getRateMax_defaultWhenNull() {
        ReleaseConfig config = createConfig(ReleaseConfigServiceImpl.KEY_RATE_MAX, null, 0);
        putInCache(config);

        BigDecimal result = releaseConfigService.getRateMax();

        assertEquals(0, new BigDecimal("0.0005").compareTo(result));
    }

    // ============== listAll 测试 ==============

    @Test
    @DisplayName("listAll: 返回 Mapper 查询结果")
    void listAll_returnsAll() {
        List<ReleaseConfig> configs = List.of(
                createConfig("rate_max", "0.0005", 0),
                createConfig("rate_min", "0.0003", 0),
                createConfig("k_min", "0.005", 1)
        );
        when(releaseConfigMapper.selectList(any())).thenReturn(configs);

        List<ReleaseConfig> result = releaseConfigService.listAll();

        assertEquals(3, result.size());
        assertEquals("rate_max", result.get(0).getConfigKey());
        assertEquals("rate_min", result.get(1).getConfigKey());
        assertEquals("k_min", result.get(2).getConfigKey());
    }

    // ============== getByKey 测试 ==============

    @Test
    @DisplayName("getByKey: 缓存命中直接返回，不走 Mapper")
    void getByKey_cacheHit() {
        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        putInCache(config);

        ReleaseConfig result = releaseConfigService.getByKey("k_min");

        assertSame(config, result);
        verify(releaseConfigMapper, never()).findByKey(anyString());
    }

    @Test
    @DisplayName("getByKey: 缓存未命中时从 Mapper 加载并回填缓存")
    void getByKey_cacheMissLoadsFromDb() {
        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        when(releaseConfigMapper.findByKey("k_min")).thenReturn(config);

        ReleaseConfig first = releaseConfigService.getByKey("k_min");
        assertNotNull(first);
        assertEquals("0.005", first.getConfigValue());

        ReleaseConfig second = releaseConfigService.getByKey("k_min");
        assertSame(first, second);

        verify(releaseConfigMapper, times(1)).findByKey("k_min");
    }

    // ============== updateConfig 测试 ==============

    @Test
    @DisplayName("updateConfig: 可编辑配置 + 双重签名 -> 更新成功")
    void updateConfig_success() {
        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        putInCache(config);

        List<String> signatures = List.of("sig-admin-001", "sig-admin-002");

        ReleaseConfig result = releaseConfigService.updateConfig("k_min", "0.006", "operatorA", signatures, "0xhash123");

        assertNotNull(result);
        assertEquals("0.006", result.getConfigValue());
        assertEquals("operatorA", result.getUpdatedBy());
        assertNotNull(result.getUpdatedAt());

        verify(releaseConfigMapper).updateById(config);
        verify(paramChangeApprovalMapper).insert(any(ParamChangeApproval.class));

        ReleaseConfig cached = releaseConfigService.getByKey("k_min");
        assertEquals("0.006", cached.getConfigValue());
    }

    @Test
    @DisplayName("updateConfig: 硬常量(editable=0)抛 BizException")
    void updateConfig_rejectsHardConstant() {
        ReleaseConfig hardConfig = createConfig("rate_max", "0.0005", 0);
        putInCache(hardConfig);

        List<String> signatures = List.of("sig-admin-001", "sig-admin-002");

        assertThrows(BizException.class,
                () -> releaseConfigService.updateConfig("rate_max", "0.0006", "op", signatures, "0xhash"));
    }

    @Test
    @DisplayName("updateConfig: 签名不足(<2人)抛 BizException")
    void updateConfig_insufficientSignatures() {
        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        putInCache(config);

        List<String> oneSignature = List.of("sig-admin-001");

        assertThrows(BizException.class,
                () -> releaseConfigService.updateConfig("k_min", "0.006", "op", oneSignature, "0xhash"));

        assertThrows(BizException.class,
                () -> releaseConfigService.updateConfig("k_min", "0.006", "op", null, "0xhash"));
    }

    // ============== refresh 测试 ==============

    @Test
    @DisplayName("refresh: 清空缓存后从 DB 重新加载")
    void refresh_clearsAndReloads() {
        putInCache(createConfig("k_min", "0.005", 1));
        putInCache(createConfig("k_max", "0.01", 1));
        assertEquals(2, cache.size());

        List<ReleaseConfig> dbConfigs = List.of(
                createConfig("rate_max", "0.0005", 0),
                createConfig("rate_min", "0.0003", 0),
                createConfig("k_min", "0.005", 1),
                createConfig("k_max", "0.01", 1),
                createConfig("alpha", "0.05", 1)
        );
        when(releaseConfigMapper.selectList(null)).thenReturn(dbConfigs);

        releaseConfigService.refresh();

        assertEquals(5, cache.size());
        assertNotNull(cache.get("rate_max"));
        assertNotNull(cache.get("rate_min"));
        assertNotNull(cache.get("alpha"));
        verify(releaseConfigMapper).selectList(null);
    }

    // ============== isEditable 测试 ==============

    @Test
    @DisplayName("isEditable: editable=1 返回 true")
    void isEditable_editableConfig() {
        putInCache(createConfig("k_min", "0.005", 1));

        assertTrue(releaseConfigService.isEditable("k_min"));
    }

    @Test
    @DisplayName("isEditable: editable=0(硬常量) 返回 false")
    void isEditable_hardConstant() {
        putInCache(createConfig("rate_max", "0.0005", 0));

        assertFalse(releaseConfigService.isEditable("rate_max"));
    }

    // ============== applyParamChange 测试 ==============

    @Test
    @DisplayName("applyParamChange: 可编辑配置申请成功，创建待审批记录")
    void applyParamChange_success() {
        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        putInCache(config);

        ParamChangeApproval approval = releaseConfigService.applyParamChange("k_min", "0.007", "operatorA", "0xhash456");

        assertNotNull(approval);
        assertEquals("k_min", approval.getConfigKey());
        assertEquals("0.005", approval.getOldValue());
        assertEquals("0.007", approval.getNewValue());
        assertEquals("operatorA", approval.getOperator());
        assertEquals("0xhash456", approval.getEvidenceTxHash());
        assertEquals(0, approval.getStatus());

        verify(paramChangeApprovalMapper).insert(any(ParamChangeApproval.class));
    }

    // ============== approveParamChange 测试 ==============

    @Test
    @DisplayName("approveParamChange: 审批通过 -> 更新配置 + 写入审批签名")
    void approveParamChange_approved() {
        ParamChangeApproval pending = new ParamChangeApproval();
        pending.setId(1L);
        pending.setConfigKey("k_min");
        pending.setOldValue("0.005");
        pending.setNewValue("0.008");
        pending.setStatus(0);

        when(paramChangeApprovalMapper.selectById(1L)).thenReturn(pending);

        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        putInCache(config);

        List<String> signatures = List.of("sig-admin-001", "sig-admin-002");

        ReleaseConfig result = releaseConfigService.approveParamChange(1L, "approverA", signatures, "OK", true);

        assertNotNull(result);
        assertEquals("0.008", result.getConfigValue());
        assertEquals("approverA", result.getUpdatedBy());
        assertNotNull(result.getUpdatedAt());

        verify(releaseConfigMapper).updateById(config);
        verify(paramChangeApprovalMapper).updateById(any(ParamChangeApproval.class));

        ReleaseConfig cached = releaseConfigService.getByKey("k_min");
        assertEquals("0.008", cached.getConfigValue());
    }

    @Test
    @DisplayName("approveParamChange: 审批拒绝 -> 配置不变 + 审批状态置为 REJECTED")
    void approveParamChange_rejected() {
        ParamChangeApproval pending = new ParamChangeApproval();
        pending.setId(2L);
        pending.setConfigKey("k_min");
        pending.setOldValue("0.005");
        pending.setNewValue("0.008");
        pending.setStatus(0);

        when(paramChangeApprovalMapper.selectById(2L)).thenReturn(pending);

        ReleaseConfig config = createConfig("k_min", "0.005", 1);
        putInCache(config);

        ReleaseConfig result = releaseConfigService.approveParamChange(2L, "approverB", null, "Not OK", false);

        assertNotNull(result);
        assertEquals("0.005", result.getConfigValue());

        verify(releaseConfigMapper, never()).updateById(any());
        verify(paramChangeApprovalMapper).updateById(any(ParamChangeApproval.class));
    }
}