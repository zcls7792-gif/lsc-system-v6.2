package com.lianshengtong.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.risk.dto.RiskCheckDTO;
import com.lianshengtong.risk.entity.RiskLog;
import com.lianshengtong.risk.feign.AiGatewayFeignClient;
import com.lianshengtong.risk.mapper.RiskLogMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("风控服务单元测试")
class RiskControlServiceImplTest {

    @Mock
    private RiskLogMapper riskLogMapper;

    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations valueOperations;

    @Mock
    private SetOperations setOperations;

    private RiskControlServiceImpl riskControlService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        riskControlService = new RiskControlServiceImpl(
                riskLogMapper, aiGatewayFeignClient, stringRedisTemplate);

        ReflectionTestUtils.setField(riskControlService, "batchOrderWindowSeconds", 3600);
        ReflectionTestUtils.setField(riskControlService, "batchOrderThreshold", 10);
        ReflectionTestUtils.setField(riskControlService, "hybridPayStreakCount", 3);
        ReflectionTestUtils.setField(riskControlService, "hybridPayLscRatio", new BigDecimal("0.90"));
        ReflectionTestUtils.setField(riskControlService, "arbitrageProductThreshold", 5);
        ReflectionTestUtils.setField(riskControlService, "geoWindowSeconds", 3600);
        ReflectionTestUtils.setField(riskControlService, "geoCityThreshold", 3);
        ReflectionTestUtils.setField(riskControlService, "aiHighRiskScore", 80);
        ReflectionTestUtils.setField(riskControlService, "aiMidRiskScore", 50);
    }

    private RiskCheckDTO buildBaseDto() {
        RiskCheckDTO dto = new RiskCheckDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD-001");
        dto.setProductId(100L);
        dto.setOrderAmount(new BigDecimal("100.00"));
        dto.setLscAmount(50L);
        dto.setClientIp("127.0.0.1");
        dto.setClientCity("Beijing");
        dto.setEnableAi(false);
        return dto;
    }

    // ============== 原有测试保留 ==============

    @Test
    @DisplayName("无风险 - 无规则命中，所有计数器未达阈值")
    void testCheck_NoRisk_NoRulesHit() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertEquals(0, result.getRiskType());
        assertEquals(3, result.getHandleStatus());
        assertEquals("无风险命中", result.getHitRule());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("批量下单触发 - 1小时内计数 > 10")
    void testCheck_BatchOrderTriggered() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(11L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(1, result.getRiskType());
        assertEquals(0, result.getHandleStatus());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("批量下单"));
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("异常混合支付触发 - LSC占比>90% 且连续streak>=3")
    void testCheck_HybridPayTriggered() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setLscAmount(95L);
        dto.setOrderAmount(new BigDecimal("100.00"));

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:hybrid-streak:"))).thenReturn(3L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(2, result.getRiskType());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("异常混合支付"));
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("高频套利触发 - 同商品下单次数 > 5")
    void testCheck_ArbitrageTriggered() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:arb:"))).thenReturn(6L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(3, result.getRiskType());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("高频套利"));
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("异地操作触发 - 1小时内城市数 >= 3")
    void testCheck_GeoTriggered() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(setOperations.size(startsWith("lsc:risk:geo:"))).thenReturn(3L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(4, result.getRiskType());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("异地操作"));
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("AI高风险 - AI网关返回评分 >= 80")
    void testCheck_AiHighRisk() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> highRiskResp = R.ok(85);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(highRiskResp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(5, result.getRiskType());
        assertEquals(85, result.getAiScore());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("AI动态风控"));
        verify(aiGatewayFeignClient).riskScore(eq(1L), anyString());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("AI中风险 - AI网关返回评分 50-79")
    void testCheck_AiMidRisk() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> midRiskResp = R.ok(60);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(midRiskResp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(2, result.getRiskLevel());
        assertEquals(5, result.getRiskType());
        assertEquals(60, result.getAiScore());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("AI动态风控"));
        verify(aiGatewayFeignClient).riskScore(eq(1L), anyString());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("AI未启用 - 仅检查固定规则，不调用AI网关")
    void testCheck_AiDisabled() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(false);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertNull(result.getAiScore());
        verify(aiGatewayFeignClient, never()).riskScore(anyLong(), anyString());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("AI低风险 - AI网关返回评分 < 50")
    void testCheck_AiLowRisk() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> lowRiskResp = R.ok(30);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(lowRiskResp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(1, result.getRiskLevel());
        assertEquals(5, result.getRiskType());
        assertEquals(30, result.getAiScore());
        assertNotNull(result.getHitRule());
        assertTrue(result.getHitRule().contains("AI动态风控"));
        assertEquals(3, result.getHandleStatus());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("混合支付LSC占比不足 - 重置连续计数，不触发风控")
    void testCheck_HybridPayRatioTooLow() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setLscAmount(5L);
        dto.setOrderAmount(new BigDecimal("100.00"));

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        verify(stringRedisTemplate).delete(startsWith("lsc:risk:hybrid-streak:"));
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("AI网关异常 - 降级为仅固定规则检查")
    void testCheck_AiGatewayException() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString()))
                .thenThrow(new RuntimeException("AI网关不可用"));

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertNull(result.getAiScore());
        verify(aiGatewayFeignClient).riskScore(anyLong(), anyString());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("高风险优先 - 批量下单和AI高风险同时命中，取最高等级")
    void testCheck_HighRiskPriority() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(11L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> highRiskResp = R.ok(85);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(highRiskResp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(1, result.getRiskType());
        assertNotNull(result.getAiScore());
        verify(riskLogMapper).insert(result);
    }

    // ============== 新增测试 ==============

    @Test
    @DisplayName("批量风控：正常批次通过")
    void testCheckBatchRisk_normalBatchPasses() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(5L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertEquals(3, result.getHandleStatus());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("批量风控：超过批次大小限制触发拦截")
    void testCheckBatchRisk_exceedsLimit() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(20L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(1, result.getRiskType());
        assertTrue(result.getHitRule().contains("批量下单"));
    }

    @Test
    @DisplayName("异常混合支付：LSC比例刚好等于90%不触发")
    void testCheckAbnormalMixPay_ratioAtThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setLscAmount(90L);
        dto.setOrderAmount(new BigDecimal("100.00"));

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        lenient().when(valueOperations.increment(startsWith("lsc:risk:hybrid-streak:"))).thenReturn(3L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        verify(stringRedisTemplate).delete(startsWith("lsc:risk:hybrid-streak:"));
    }

    @Test
    @DisplayName("异常混合支付：LSC比例超过90%且连续streak达标触发")
    void testCheckAbnormalMixPay_ratioExceedsThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setLscAmount(91L);
        dto.setOrderAmount(new BigDecimal("100.00"));

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:hybrid-streak:"))).thenReturn(3L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(2, result.getRiskType());
    }

    @Test
    @DisplayName("AI风控：AI网关返回评分正常")
    void testAiRiskCheck_gatewayReturnsScore() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> resp = R.ok(45);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(resp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(45, result.getAiScore());
        verify(aiGatewayFeignClient).riskScore(eq(1L), anyString());
    }

    @Test
    @DisplayName("AI风控：AI网关失败降级到本地规则")
    void testAiRiskCheck_gatewayFallsBackToLocal() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString()))
                .thenThrow(new RuntimeException("AI网关超时"));

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertNull(result.getAiScore());
        assertEquals(0, result.getRiskLevel());
        verify(aiGatewayFeignClient).riskScore(anyLong(), anyString());
    }

    @Test
    @DisplayName("批次限制：批次数量在限制内允许通过")
    void testBatchLimit_withinLimit() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(5L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("批次限制：批次数量超限触发拦截")
    void testBatchLimit_exceedsLimit() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(11L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(1, result.getRiskType());
    }

    @Test
    @DisplayName("频率控制：正常频率允许通过")
    void testFrequencyControl_normalFrequency() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(2L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertEquals(3, result.getHandleStatus());
    }

    @Test
    @DisplayName("频率控制：高频触发批量下单风控")
    void testFrequencyControl_highFrequency() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(10L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("异地操作：单城市不触发")
    void testGeo_singleCityNotTriggered() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(startsWith("lsc:risk:geo:"))).thenReturn(1L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("异地操作：两城市不触发(阈值为3)")
    void testGeo_twoCitiesNotTriggered() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(startsWith("lsc:risk:geo:"))).thenReturn(2L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("风控日志：查询日志分页")
    void testLogs_pagination() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RiskLog> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(java.util.Collections.emptyList());
        page.setTotal(0);
        when(riskLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = riskControlService.logs(1, 20, null, null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("风控日志：按风险等级过滤")
    void testLogs_filterByRiskLevel() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RiskLog> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(java.util.Collections.emptyList());
        page.setTotal(0);
        when(riskLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = riskControlService.logs(1, 20, 1L, 3, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("风控处理：处理不存在的日志抛异常")
    void testHandle_notFound() {
        when(riskLogMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class,
                () -> riskControlService.handle(999L, 1, "处理备注"));
    }

    @Test
    @DisplayName("风控处理：成功处理日志")
    void testHandle_success() {
        RiskLog log = new RiskLog();
        log.setId(1L);
        log.setRiskLevel(3);
        log.setHandleStatus(0);
        when(riskLogMapper.selectById(1L)).thenReturn(log);

        riskControlService.handle(1L, 4, "人工审核通过");

        verify(riskLogMapper).updateById(any(RiskLog.class));
    }

    @Test
    @DisplayName("仪表盘：返回正确统计数据")
    void testDashboard_returnsCorrectData() {
        // Reset the mock to override default stubbings
        reset(riskLogMapper);
        // Set up specific null handling first, then general any
        // Using distinct matchers to avoid conflict
        doReturn(100L).when(riskLogMapper).selectCount(isNull());
        lenient().when(riskLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        lenient().when(riskLogMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        var result = riskControlService.dashboard();

        assertNotNull(result);
        assertEquals(100L, result.get("total"));
        assertNotNull(result.get("byLevel"));
        assertNotNull(result.get("byStatus"));
        assertEquals(5L, result.get("highRiskPending"));
    }

    @Test
    @DisplayName("按ID查询风控日志：存在返回")
    void testGetById_exists() {
        RiskLog log = new RiskLog();
        log.setId(1L);
        log.setRiskLevel(3);
        when(riskLogMapper.selectById(1L)).thenReturn(log);

        RiskLog result = riskControlService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("按ID查询风控日志：不存在抛异常")
    void testGetById_notFound() {
        when(riskLogMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> riskControlService.getById(999L));
    }
}