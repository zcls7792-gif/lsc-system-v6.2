package com.lianshengtong.release.service.impl;

import com.lianshengtong.common.enums.ReleaseTaskStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.release.alert.AlertChannel;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.entity.ParamChangeApproval;
import com.lianshengtong.release.entity.ReleaseConfig;
import com.lianshengtong.release.feign.LscLedgerFeignClient;
import com.lianshengtong.release.mapper.DailyReleaseSummaryMapper;
import com.lianshengtong.release.mapper.ParamChangeApprovalMapper;
import com.lianshengtong.release.mapper.ReleaseConfigMapper;
import com.lianshengtong.release.service.ReleaseConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("释放服务边界用例测试")
class ReleaseEdgeCaseTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 8, 15);

    @Nested
    @DisplayName("BatchReleaseServiceImpl 边界测试")
    class BatchReleaseEdgeTests {

        @Mock
        private LscLedgerFeignClient lscLedgerFeignClient;
        @Mock
        private DailyReleaseSummaryMapper dailyReleaseSummaryMapper;
        @Mock
        private RedissonClient redissonClient;
        @Mock
        private RLock rLock;

        @InjectMocks
        private BatchReleaseServiceImpl batchReleaseService;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(batchReleaseService, "batchSize", 100000);
            ReflectionTestUtils.setField(batchReleaseService, "lockKey", "lsc:release:lock:daily");
            ReflectionTestUtils.setField(batchReleaseService, "lockExpireSeconds", 7200L);

            lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
            try {
                lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
            } catch (InterruptedException ignored) {}
            lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
        }

        private DailyReleaseSummary buildSummary(long tRelease, BigDecimal rate) {
            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setDate(TEST_DATE);
            s.setTRelease(tRelease);
            s.setRate(rate);
            s.setActualReleased(0L);
            s.setBatchCount(0);
            s.setFailedBatchCount(0);
            return s;
        }

        private void setupLockedSummary(Map<String, Object>... accounts) {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> accountList = new ArrayList<>();
            Collections.addAll(accountList, accounts);
            data.put("accounts", accountList);
            R<Map<String, Object>> resp = R.ok(data);
            lenient().when(lscLedgerFeignClient.lockedSummary()).thenReturn(resp);
        }

        private Map<String, Object> accountMap(Long userId, Long totalLocked) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", userId);
            m.put("totalLocked", totalLocked);
            return m;
        }

        @Test
        @DisplayName("executeBatchRelease: rate=0且planTotal=0跳过加载直接成功")
        void execute_zeroRate_zeroPlanTotal_success() {
            DailyReleaseSummary summary = buildSummary(0L, BigDecimal.ZERO);
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertEquals(ReleaseTaskStatusEnum.SUCCESS.getCode(), result.getStatus());
        }

        @Test
        @DisplayName("executeBatchRelease: lLocked为null时按0处理导致无待释放")
        void execute_nullLLocked_noItems() {
            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0004"));
            summary.setTRelease(0L);
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertEquals(0L, result.getActualReleased());
        }

        @Test
        @DisplayName("loadPendingReleaseItems: lockedSummary返回null安全处理")
        void loadItems_nullResp_emptyList() {
            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0004"));
            when(lscLedgerFeignClient.lockedSummary()).thenReturn(null);
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("loadPendingReleaseItems: accounts非List类型安全处理")
        void loadItems_wrongAccountsType_emptyList() {
            Map<String, Object> data = new HashMap<>();
            data.put("accounts", "not_a_list");
            when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(data));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("loadPendingReleaseItems: 账户记录userId/locked为null跳过")
        void loadItems_nullFields_skipAccount() {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Object> bad1 = new HashMap<>();
            bad1.put("userId", null);
            bad1.put("totalLocked", 100L);
            list.add(bad1);
            Map<String, Object> bad2 = new HashMap<>();
            bad2.put("userId", 1L);
            bad2.put("totalLocked", null);
            list.add(bad2);
            data.put("accounts", list);
            when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(data));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("loadPendingReleaseItems: locked<=0的账户跳过")
        void loadItems_negativeLocked_skip() {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Object> acc = new HashMap<>();
            acc.put("userId", 1L);
            acc.put("totalLocked", 0L);
            list.add(acc);
            data.put("accounts", list);
            when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(data));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("loadPendingReleaseItems: 单笔释放量为0的用户跳过")
        void loadItems_zeroRelease_skip() {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> list = new ArrayList<>();
            Map<String, Object> acc = new HashMap<>();
            acc.put("userId", 1L);
            acc.put("totalLocked", 100L);
            list.add(acc);
            data.put("accounts", list);
            when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(data));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0001"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("loadPendingReleaseItems: 锁定明细拉取RuntimeException被捕获")
        void loadItems_runtimeException_caught() {
            when(lscLedgerFeignClient.lockedSummary()).thenThrow(new RuntimeException("远程服务异常"));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("executeBatchRelease: 账本返回null响应抛异常")
        void execute_nullRespFromLedger_fails() {
            setupLockedSummary(accountMap(1L, 1_000_000L));
            when(lscLedgerFeignClient.releaseBatch(anyList())).thenReturn(null);
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertEquals(ReleaseTaskStatusEnum.FAILED.getCode(), result.getStatus());
        }

        @Test
        @DisplayName("executeBatchRelease: lockedSummary失败但不抛异常时返回空列表")
        void execute_lockedSummaryFail_emptyItems() {
            when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.fail("拉取锁定汇总失败"));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertNotNull(result);
        }

        @Test
        @DisplayName("executeBatchRelease: 释放量为负时标记批次失败")
        void execute_negativeReleaseAmount_fails() {
            Map<String, Object> data = new HashMap<>();
            data.put("releasedAmount", -1L);
            when(lscLedgerFeignClient.releaseBatch(anyList())).thenReturn(R.ok(data));
            setupLockedSummary(accountMap(1L, 1_000_000L));
            when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

            DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
            DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);
            assertEquals(ReleaseTaskStatusEnum.FAILED.getCode(), result.getStatus());
        }

        @Test
        @DisplayName("reconcile: tRelease和actualReleased均为null时视为相等")
        void reconcile_bothNull_equal() {
            DailyReleaseSummary s = new DailyReleaseSummary();
            assertTrue(batchReleaseService.reconcile(s));
        }
    }

    @Nested
    @DisplayName("ReleaseCalcServiceImpl 边界测试")
    class ReleaseCalcEdgeTests {

        @Mock
        private ReleaseConfigService releaseConfigService;
        @Mock
        private AlertChannel alertChannel;

        @InjectMocks
        private ReleaseCalcServiceImpl releaseCalcService;

        private static final BigDecimal RATE_MAX = new BigDecimal("0.0005");
        private static final BigDecimal RATE_MIN = new BigDecimal("0.0003");
        private static final BigDecimal K_MIN = new BigDecimal("0.0050");
        private static final BigDecimal K_MAX = new BigDecimal("0.0100");
        private static final BigDecimal ALPHA = new BigDecimal("0.05");

        @BeforeEach
        void setUp() {
            lenient().when(releaseConfigService.getRateMax()).thenReturn(RATE_MAX);
            lenient().when(releaseConfigService.getRateMin()).thenReturn(RATE_MIN);
            lenient().when(releaseConfigService.getKMin()).thenReturn(K_MIN);
            lenient().when(releaseConfigService.getKMax()).thenReturn(K_MAX);
            lenient().when(releaseConfigService.getAlpha()).thenReturn(ALPHA);
            ReflectionTestUtils.setField(releaseCalcService, "alertReceivers", "admin");
        }

        @Test
        @DisplayName("calcK: nTotal为null返回0")
        void calcK_nullN_returnsZero() {
            BigDecimal k = releaseCalcService.calcK(null, new BigDecimal("100"));
            assertEquals(0, BigDecimal.ZERO.compareTo(k));
        }

        @Test
        @DisplayName("calcK: mTotal为null返回0")
        void calcK_nullM_returnsZero() {
            BigDecimal k = releaseCalcService.calcK(new BigDecimal("100"), null);
            assertEquals(0, BigDecimal.ZERO.compareTo(k));
        }

        @Test
        @DisplayName("calcK: mTotal为0返回0")
        void calcK_zeroM_returnsZero() {
            BigDecimal k = releaseCalcService.calcK(new BigDecimal("100"), BigDecimal.ZERO);
            assertEquals(0, BigDecimal.ZERO.compareTo(k));
        }

        @Test
        @DisplayName("calcK: mTotal为负数返回0")
        void calcK_negativeM_returnsZero() {
            BigDecimal k = releaseCalcService.calcK(new BigDecimal("100"), new BigDecimal("-1"));
            assertEquals(0, BigDecimal.ZERO.compareTo(k));
        }

        @Test
        @DisplayName("calcK: 保留6位小数")
        void calcK_scale6() {
            BigDecimal k = releaseCalcService.calcK(new BigDecimal("1"), new BigDecimal("3"));
            assertEquals(6, k.scale());
        }

        @Test
        @DisplayName("calcReleaseTotal: lLocked为0返回0")
        void calcReleaseTotal_zero_returns0() {
            assertEquals(0L, releaseCalcService.calcReleaseTotal(RATE_MAX, 0L));
        }

        @Test
        @DisplayName("calcReleaseTotal: lLocked为负数返回0")
        void calcReleaseTotal_negative_returns0() {
            assertEquals(0L, releaseCalcService.calcReleaseTotal(RATE_MAX, -1L));
        }

        @Test
        @DisplayName("calcReleaseTotal: lLocked很小导致释放量为0")
        void calcReleaseTotal_smallAmount_returns0() {
            assertEquals(0L, releaseCalcService.calcReleaseTotal(new BigDecimal("0.0005"), 1L));
        }

        @Test
        @DisplayName("validateRate: 越界触发告警渠道发送")
        void validateRate_outOfRange_triggersAlert() {
            releaseCalcService.validateRate(new BigDecimal("0.0010"));
            verify(alertChannel).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("calcDailyRelease: 保留传入date不覆盖")
        void calcDailyRelease_preservesDate() {
            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setMTotal(new BigDecimal("1000000"));
            s.setNTotal(new BigDecimal("5000"));
            s.setLLocked(1_000_000L);
            LocalDate originalDate = LocalDate.of(2020, 1, 1);
            s.setDate(originalDate);

            DailyReleaseSummary result = releaseCalcService.calcDailyRelease(s);
            assertEquals(originalDate, result.getDate());
        }

        @Test
        @DisplayName("calcDailyRelease: date为null时自动填充当前日期")
        void calcDailyRelease_nullDate_fillsToday() {
            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setMTotal(new BigDecimal("1000000"));
            s.setNTotal(new BigDecimal("5000"));
            s.setLLocked(1_000_000L);

            DailyReleaseSummary result = releaseCalcService.calcDailyRelease(s);
            assertNotNull(result.getDate());
            assertEquals(LocalDate.now(), result.getDate());
        }

        @Test
        @DisplayName("calcDailyRelease: lLocked为null按0处理")
        void calcDailyRelease_nullLLocked_zeroRelease() {
            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setMTotal(new BigDecimal("1000000"));
            s.setNTotal(new BigDecimal("5000"));
            s.setLLocked(null);

            DailyReleaseSummary result = releaseCalcService.calcDailyRelease(s);
            assertEquals(0L, result.getTRelease());
        }

        @Test
        @DisplayName("calcDailyRelease: rate越界抛BizException")
        void calcDailyRelease_rateOutOfRange_throws() {
            BigDecimal extremeRate = new BigDecimal("0.0001");
            lenient().when(releaseConfigService.getRateMin()).thenReturn(new BigDecimal("0.0002"));
            lenient().when(releaseConfigService.getRateMax()).thenReturn(new BigDecimal("0.00015"));

            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setMTotal(new BigDecimal("1000000"));
            s.setNTotal(new BigDecimal("5000"));
            s.setLLocked(1000L);

            assertThrows(BizException.class, () -> releaseCalcService.calcDailyRelease(s));
        }

        @Test
        @DisplayName("calcDailyRelease: k值计算正确传播")
        void calcDailyRelease_kCalculated() {
            DailyReleaseSummary s = new DailyReleaseSummary();
            s.setMTotal(new BigDecimal("1000000"));
            s.setNTotal(new BigDecimal("5000"));
            s.setLLocked(1_000_000L);

            DailyReleaseSummary result = releaseCalcService.calcDailyRelease(s);
            assertNotNull(result.getK());
            assertEquals(0, new BigDecimal("0.005000").compareTo(result.getK()));
        }
    }

    @Nested
    @DisplayName("ReleaseConfigServiceImpl 边界测试")
    class ReleaseConfigEdgeTests {

        @Mock
        private ReleaseConfigMapper releaseConfigMapper;
        @Mock
        private ParamChangeApprovalMapper paramChangeApprovalMapper;

        @InjectMocks
        private ReleaseConfigServiceImpl releaseConfigService;

        private ReleaseConfig buildConfig(String key, String value, int editable) {
            ReleaseConfig c = new ReleaseConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            c.setEditable(editable);
            c.setDescription("Test config " + key);
            c.setUpdatedBy("admin");
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        }

        @Test
        @DisplayName("getRateMax: 使用缓存值返回")
        void getRateMax_cachedValue() {
            ReleaseConfig config = buildConfig("rate_max", "0.0005", 0);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(config));
            releaseConfigService.refresh();

            BigDecimal rateMax = releaseConfigService.getRateMax();
            assertEquals(0, new BigDecimal("0.0005").compareTo(rateMax));
        }

        @Test
        @DisplayName("getRateMin: 默认值回退")
        void getRateMin_defaultFallback() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            BigDecimal rateMin = releaseConfigService.getRateMin();
            assertEquals(0, new BigDecimal("0.0003").compareTo(rateMin));
        }

        @Test
        @DisplayName("getKMin: 默认值回退")
        void getKMin_defaultFallback() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            assertEquals(0, new BigDecimal("0.005").compareTo(releaseConfigService.getKMin()));
        }

        @Test
        @DisplayName("getKMax: 默认值回退")
        void getKMax_defaultFallback() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            assertEquals(0, new BigDecimal("0.01").compareTo(releaseConfigService.getKMax()));
        }

        @Test
        @DisplayName("getAlpha: 默认值回退")
        void getAlpha_defaultFallback() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            assertEquals(0, new BigDecimal("0.05").compareTo(releaseConfigService.getAlpha()));
        }

        @Test
        @DisplayName("refresh: 加载空列表后getByKey返回null")
        void refresh_emptyList_getByKeyNull() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            ReleaseConfig c = releaseConfigService.getByKey("nonexistent");
            assertNull(c);
        }

        @Test
        @DisplayName("isEditable: 配置不存在返回false")
        void isEditable_notFound_false() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            assertFalse(releaseConfigService.isEditable("rate_max"));
        }

        @Test
        @DisplayName("isEditable: editable=1返回true")
        void isEditable_yes() {
            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            assertTrue(releaseConfigService.isEditable("k_min"));
        }

        @Test
        @DisplayName("isEditable: editable=0返回false")
        void isEditable_no() {
            ReleaseConfig c = buildConfig("rate_max", "0.0005", 0);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            assertFalse(releaseConfigService.isEditable("rate_max"));
        }

        @Test
        @DisplayName("updateConfig: 硬常量修改抛异常")
        void updateConfig_editableZero_throws() {
            ReleaseConfig c = buildConfig("rate_max", "0.0005", 0);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            assertThrows(BizException.class, () ->
                    releaseConfigService.updateConfig("rate_max", "0.001", "admin", List.of("sig1", "sig2"), "hash"));
        }

        @Test
        @DisplayName("updateConfig: 签名不足抛异常")
        void updateConfig_insufficientSignatures_throws() {
            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            assertThrows(BizException.class, () ->
                    releaseConfigService.updateConfig("k_min", "0.006", "admin", List.of("sig1"), "hash"));
        }

        @Test
        @DisplayName("updateConfig: 签名为null抛异常")
        void updateConfig_nullSignatures_throws() {
            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            assertThrows(BizException.class, () ->
                    releaseConfigService.updateConfig("k_min", "0.006", "admin", null, "hash"));
        }

        @Test
        @DisplayName("updateConfig: 配置不存在抛异常")
        void updateConfig_notFound_throws() {
            when(releaseConfigMapper.selectList(null)).thenReturn(Collections.emptyList());
            releaseConfigService.refresh();

            assertThrows(BizException.class, () ->
                    releaseConfigService.updateConfig("missing", "v", "admin", List.of("s1", "s2"), "hash"));
        }

        @Test
        @DisplayName("updateConfig: 成功更新配置并写入审批记录")
        void updateConfig_success() {
            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            when(releaseConfigMapper.updateById(any(ReleaseConfig.class))).thenReturn(1);
            when(paramChangeApprovalMapper.insert(any(ParamChangeApproval.class))).thenReturn(1);
            releaseConfigService.refresh();

            ReleaseConfig result = releaseConfigService.updateConfig(
                    "k_min", "0.006", "admin", List.of("sig1", "sig2"), "0xhash123");

            assertNotNull(result);
            assertEquals("0.006", result.getConfigValue());
            assertEquals("admin", result.getUpdatedBy());
        }

        @Test
        @DisplayName("applyParamChange: 硬常量申请抛异常")
        void applyParamChange_editableZero_throws() {
            ReleaseConfig c = buildConfig("rate_max", "0.0005", 0);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            assertThrows(BizException.class, () ->
                    releaseConfigService.applyParamChange("rate_max", "0.001", "admin", "hash"));
        }

        @Test
        @DisplayName("applyParamChange: 成功提交变更申请")
        void applyParamChange_success() {
            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            when(paramChangeApprovalMapper.insert(any(ParamChangeApproval.class))).thenReturn(1);
            releaseConfigService.refresh();

            ParamChangeApproval approval = releaseConfigService.applyParamChange(
                    "k_min", "0.006", "admin", "hash");

            assertNotNull(approval);
            assertEquals("k_min", approval.getConfigKey());
            assertEquals("0.005", approval.getOldValue());
            assertEquals("0.006", approval.getNewValue());
            assertEquals(0, approval.getStatus());
        }

        @Test
        @DisplayName("approveParamChange: 审批记录不存在抛异常")
        void approveParamChange_notFound_throws() {
            when(paramChangeApprovalMapper.selectById(999L)).thenReturn(null);

            assertThrows(BizException.class, () ->
                    releaseConfigService.approveParamChange(999L, "admin", List.of("s1", "s2"), "ok", true));
        }

        @Test
        @DisplayName("approveParamChange: 已处理记录不可重复审批")
        void approveParamChange_alreadyProcessed_throws() {
            ParamChangeApproval approval = new ParamChangeApproval();
            approval.setId(1L);
            approval.setStatus(1);
            when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

            assertThrows(BizException.class, () ->
                    releaseConfigService.approveParamChange(1L, "admin", List.of("s1", "s2"), "ok", true));
        }

        @Test
        @DisplayName("approveParamChange: 审批通过时双重签名不足抛异常")
        void approveParamChange_approve_insufficientSignatures() {
            ParamChangeApproval approval = new ParamChangeApproval();
            approval.setId(1L);
            approval.setStatus(0);
            approval.setConfigKey("k_min");
            when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

            assertThrows(BizException.class, () ->
                    releaseConfigService.approveParamChange(1L, "admin", List.of("s1"), "ok", true));
        }

        @Test
        @DisplayName("approveParamChange: 审批通过成功更新配置")
        void approveParamChange_approve_success() {
            ParamChangeApproval approval = new ParamChangeApproval();
            approval.setId(1L);
            approval.setStatus(0);
            approval.setConfigKey("k_min");
            approval.setNewValue("0.006");
            when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);

            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            when(releaseConfigMapper.updateById(any(ReleaseConfig.class))).thenReturn(1);
            when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);
            releaseConfigService.refresh();

            ReleaseConfig result = releaseConfigService.approveParamChange(
                    1L, "admin", List.of("s1", "s2"), "approved", true);

            assertNotNull(result);
        }

        @Test
        @DisplayName("approveParamChange: 审批拒绝成功")
        void approveParamChange_reject_success() {
            ParamChangeApproval approval = new ParamChangeApproval();
            approval.setId(1L);
            approval.setStatus(0);
            approval.setConfigKey("k_min");
            when(paramChangeApprovalMapper.selectById(1L)).thenReturn(approval);
            when(paramChangeApprovalMapper.updateById(any(ParamChangeApproval.class))).thenReturn(1);

            ReleaseConfig c = buildConfig("k_min", "0.005", 1);
            when(releaseConfigMapper.selectList(null)).thenReturn(List.of(c));
            releaseConfigService.refresh();

            ReleaseConfig result = releaseConfigService.approveParamChange(
                    1L, "admin", List.of("s1", "s2"), "rejected", false);

            assertNotNull(result);
        }

        @Test
        @DisplayName("listAll: 返回所有配置列表")
        void listAll_returnsList() {
            when(releaseConfigMapper.selectList(any())).thenReturn(Collections.emptyList());
            List<ReleaseConfig> result = releaseConfigService.listAll();
            assertNotNull(result);
        }
    }
}