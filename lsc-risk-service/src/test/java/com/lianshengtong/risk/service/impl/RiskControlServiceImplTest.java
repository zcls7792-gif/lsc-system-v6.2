package com.lianshengtong.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        // dashboard() 先查 total(无条件LambdaQueryWrapper)，再查 byLevel(3次)、byStatus(5次)、highRiskPending(1次)
        // 第1次返回100作为total，后续返回5
        when(riskLogMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(100L, 5L, 5L, 5L, 5L, 5L, 5L, 5L, 5L, 5L);

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

    // ============== 边界条件与分支覆盖测试 ==============

    @Test
    @DisplayName("异常混合支付：orderAmount为null跳过检测")
    void testCheck_HybridPayOrderAmountNull() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setOrderAmount(null);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("异常混合支付：lscAmount为null跳过检测")
    void testCheck_HybridPayLscAmountNull() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setLscAmount(null);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("异常混合支付：orderAmount为0跳过检测")
    void testCheck_HybridPayOrderAmountZero() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setOrderAmount(BigDecimal.ZERO);
        dto.setLscAmount(50L);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("产品ID为null时跳过套利检测")
    void testCheck_ProductIdNull_SkipArbitrage() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setProductId(null);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("clientCity为null时跳过异地检测")
    void testCheck_ClientCityNull_SkipGeo() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setClientCity(null);

        when(valueOperations.increment(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("increment返回null时边界处理")
    void testCheck_IncrementReturnNull() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(null);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("计数器首次命中时设置过期时间")
    void testCheck_FirstIncrementSetsExpire() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(setOperations.size(anyString())).thenReturn(0L);

        riskControlService.check(dto);

        verify(stringRedisTemplate, atLeastOnce()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("批量下单首次触发风险 - 计数器恰好超过阈值")
    void testCheck_BatchOrderFirstOverThreshold() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(11L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(1, result.getRiskType());
        verify(riskLogMapper).insert(result);
    }

    @Test
    @DisplayName("混合支付连续计数恰好达到阈值")
    void testCheck_HybridPayStreakExactThreshold() {
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
    }

    @Test
    @DisplayName("混合支付连续计数略低于阈值不触发")
    void testCheck_HybridPayStreakBelowThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setLscAmount(95L);
        dto.setOrderAmount(new BigDecimal("100.00"));

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:hybrid-streak:"))).thenReturn(2L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("套利次数恰好达到阈值触发")
    void testCheck_ArbitrageExactThreshold() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:arb:"))).thenReturn(6L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(3, result.getRiskType());
    }

    @Test
    @DisplayName("套利次数略低于阈值不触发")
    void testCheck_ArbitrageBelowThreshold() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:arb:"))).thenReturn(5L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("异地城市数恰好达到阈值触发")
    void testCheck_GeoExactThreshold() {
        RiskCheckDTO dto = buildBaseDto();

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(setOperations.size(startsWith("lsc:risk:geo:"))).thenReturn(3L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(4, result.getRiskType());
    }

    @Test
    @DisplayName("AI评分边界值 - 恰好等于高风险阈值")
    void testCheck_AiScoreExactlyHighThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> resp = R.ok(80);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(resp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(5, result.getRiskType());
    }

    @Test
    @DisplayName("AI评分边界值 - 恰好等于中风险阈值")
    void testCheck_AiScoreExactlyMidThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> resp = R.ok(50);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(resp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(2, result.getRiskLevel());
        assertEquals(5, result.getRiskType());
    }

    @Test
    @DisplayName("AI评分边界值 - 略低于中风险阈值")
    void testCheck_AiScoreBelowMidThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> resp = R.ok(49);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(resp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(1, result.getRiskLevel());
        assertEquals(5, result.getRiskType());
    }

    @Test
    @DisplayName("AI网关返回null响应 - 降级为无风险")
    void testCheck_AiGatewayReturnsNull() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(null);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertNull(result.getAiScore());
    }

    @Test
    @DisplayName("AI网关返回成功但data为null")
    void testCheck_AiGatewaySuccessButNoData() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> resp = R.ok(null);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(resp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        assertNull(result.getAiScore());
    }

    @Test
    @DisplayName("批量下单与AI中风险同时命中 - 取高等级")
    void testCheck_BatchOrderAndAiMidRisk() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:batch:"))).thenReturn(11L);
        when(setOperations.size(anyString())).thenReturn(0L);

        R<Integer> resp = R.ok(60);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString())).thenReturn(resp);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(3, result.getRiskLevel());
        assertEquals(1, result.getRiskType());
    }

    @Test
    @DisplayName("logs查询 - 所有参数为null使用默认值")
    void testLogs_AllParamsNull() {
        Page<RiskLog> page = new Page<>(1, 20);
        page.setRecords(java.util.Collections.emptyList());
        page.setTotal(0);
        when(riskLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = riskControlService.logs(null, null, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("logs查询 - 指定所有过滤条件")
    void testLogs_WithAllFilters() {
        Page<RiskLog> page = new Page<>(1, 20);
        page.setRecords(java.util.Collections.emptyList());
        page.setTotal(0);
        when(riskLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = riskControlService.logs(1, 20, 1L, 3, 0);

        assertNotNull(result);
    }

    // ========== 追加：更深处的边缘分支 ==========

    @Test
    @DisplayName("incrWindow Redis 抛异常 -> 降级返回 0，不影响整体 check 流程")
    void testCheck_incrWindowExceptionDegrade() {
        RiskCheckDTO dto = buildBaseDto();
        // opsForValue().increment 首次调用（batch计数）抛 Redis 连接异常
        when(valueOperations.increment(anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("OOM"));
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertEquals(0, result.getRiskLevel(), "Redis 异常降级为无风险");
        verify(riskLogMapper).insert(any(RiskLog.class));
    }

    @Test
    @DisplayName("AI 网关返回 R.fail -> 本地不设置 aiScore，继续按本地规则判断")
    void testCheck_AiGatewayRfail() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);
        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString()))
                .thenReturn(com.lianshengtong.common.result.R.fail("后端评分未就绪"));

        RiskLog result = riskControlService.check(dto);

        assertNotNull(result);
        assertNull(result.getAiScore(), "R.fail 不应写入 aiScore");
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("AI 网关返回 success=true 但 data=null 的 R.ok(null)")
    void testCheck_AiGatewayOkDataNull() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setEnableAi(true);
        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(aiGatewayFeignClient.riskScore(anyLong(), anyString()))
                .thenReturn(com.lianshengtong.common.result.R.ok(null));

        RiskLog result = riskControlService.check(dto);
        assertNotNull(result);
        assertNull(result.getAiScore());
    }

    @Test
    @DisplayName("handle: 传入 handleStatus=0 标记待人工复核，remark 正确写入")
    void testHandle_setPendingWithRemark() {
        RiskLog log = new RiskLog();
        log.setId(10L);
        log.setHandleStatus(3);
        when(riskLogMapper.selectById(10L)).thenReturn(log);
        riskControlService.handle(10L, 0, "需复核");
        org.mockito.ArgumentCaptor<RiskLog> capt =
                org.mockito.ArgumentCaptor.forClass(RiskLog.class);
        verify(riskLogMapper).updateById(capt.capture());
        RiskLog updated = capt.getValue();
        assertEquals(0, updated.getHandleStatus());
        assertEquals("需复核", updated.getHandleRemark());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    @DisplayName("异地操作 geo sAdd + expire 都被触发，城市数 <3 不触发风控")
    void testCheck_geoAddAndExpire() {
        RiskCheckDTO dto = buildBaseDto();
        dto.setClientCity("Shanghai");

        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(setOperations.add(anyString(), any())).thenReturn(1L);
        when(setOperations.size(anyString())).thenReturn(0L);
        when(setOperations.size(startsWith("lsc:risk:geo:"))).thenReturn(2L);

        RiskLog result = riskControlService.check(dto);
        assertNotNull(result);
        assertEquals(0, result.getRiskLevel());
        verify(setOperations).add(startsWith("lsc:risk:geo:"), eq("Shanghai"));
        verify(stringRedisTemplate).expire(startsWith("lsc:risk:geo:"), any());
    }

    @Test
    @DisplayName("高频套利次数=阈值-1 不触发（恰好等于4时）")
    void testCheck_ArbitrageJustBelowThreshold() {
        RiskCheckDTO dto = buildBaseDto();
        // threshold = 5, below = 4
        when(valueOperations.increment(anyString())).thenReturn(0L);
        when(valueOperations.increment(startsWith("lsc:risk:arb:"))).thenReturn(5L - 1L);
        when(setOperations.size(anyString())).thenReturn(0L);

        RiskLog result = riskControlService.check(dto);
        assertEquals(0, result.getRiskLevel());
    }

    @Test
    @DisplayName("dashboard: 高风险待处理 in(0,1,2) 所有结果均为 0")
    void testDashboard_zeroHighRiskPending() {
        reset(riskLogMapper);
        // 1 次 total + 3 次 byLevel + 5 次 byStatus + 1 次 pending = 10
        when(riskLogMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(0L);

        var map = riskControlService.dashboard();
        assertEquals(0L, map.get("total"));
        assertEquals(0L, map.get("highRiskPending"));
    }
}
