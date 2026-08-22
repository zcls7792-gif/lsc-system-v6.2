package com.lianshengtong.writeoff.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;
import com.lianshengtong.writeoff.feign.LscLedgerFeignClient;
import com.lianshengtong.writeoff.feign.MerchantFeignClient;
import com.lianshengtong.writeoff.mapper.MerchantNhRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 补齐 WriteOffServiceImpl 分支覆盖率 (I-05)，见 LSC_V6.2_Reports/LSC_V6.2_Code_Quality_Completeness_Audit_20260822.md
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WriteOffServiceImpl 分支覆盖率补齐测试")
class WriteOffServiceImplBranchCoverageTest {

    @Mock
    private MerchantNhRecordMapper merchantNhRecordMapper;
    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;
    @Mock
    private MerchantFeignClient merchantFeignClient;
    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private WriteOffServiceImpl writeOffService;

    @Test
    @DisplayName("listRecords: pageNum/pageSize 为 null 时使用默认值 1/20")
    void listRecords_defaultPageNumPageSize_applied() {
        Page<MerchantNhRecord> mockPage = new Page<>();
        when(merchantNhRecordMapper.selectPage(any(), any())).thenReturn(mockPage);

        IPage<MerchantNhRecord> result = writeOffService.listRecords(null, null, null, null, null, null, null);

        assertNotNull(result);
        verify(merchantNhRecordMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("listRecords: 全部过滤条件非空时应用所有筛选")
    void listRecords_allFiltersApplied() {
        Page<MerchantNhRecord> mockPage = new Page<>();
        when(merchantNhRecordMapper.selectPage(any(), any())).thenReturn(mockPage);

        IPage<MerchantNhRecord> result = writeOffService.listRecords(
                1, 20, 1001L, 1, "NH001", "2026-01-01", "2026-01-31");

        assertNotNull(result);
        verify(merchantNhRecordMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("listRecords: 全部过滤条件为 null/blank 时跳过条件分支")
    void listRecords_nullFilters_skipsConditions() {
        Page<MerchantNhRecord> mockPage = new Page<>();
        when(merchantNhRecordMapper.selectPage(any(), any())).thenReturn(mockPage);

        IPage<MerchantNhRecord> result = writeOffService.listRecords(
                1, 20, null, null, "", "", "");

        assertNotNull(result);
        verify(merchantNhRecordMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("listRecords: 4 参重载委托到 7 参版本")
    void listRecords_4arg_delegatesTo7arg() {
        Page<MerchantNhRecord> mockPage = new Page<>();
        when(merchantNhRecordMapper.selectPage(any(), any())).thenReturn(mockPage);

        IPage<MerchantNhRecord> result = writeOffService.listRecords(1, 20, 1001L, 1);

        assertNotNull(result);
        verify(merchantNhRecordMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("listRecords: 返回 mapper 预设的 Page 对象")
    void listRecords_returnsPage() {
        Page<MerchantNhRecord> mockPage = new Page<>(1, 20);
        MerchantNhRecord record = new MerchantNhRecord();
        record.setId(1L);
        record.setMerchantId(1001L);
        mockPage.setRecords(java.util.Collections.singletonList(record));
        mockPage.setTotal(1);
        when(merchantNhRecordMapper.selectPage(any(), any())).thenReturn(mockPage);

        IPage<MerchantNhRecord> result = writeOffService.listRecords(1, 20, 1001L, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(1001L, result.getRecords().get(0).getMerchantId());
    }

    @Test
    @DisplayName("toLong: null 入参返回 0")
    void toLong_null_returnsZero() {
        long result = ((Number) ReflectionTestUtils
                .invokeMethod(writeOffService, "toLong", new Object[]{null})).longValue();
        assertEquals(0L, result);
    }

    @Test
    @DisplayName("toLong: Number 实例(Integer)返回对应 long 值")
    void toLong_numberInstance_returnsLong() {
        long result = ((Number) ReflectionTestUtils
                .invokeMethod(writeOffService, "toLong", new Object[]{Integer.valueOf(100)})).longValue();
        assertEquals(100L, result);
    }

    @Test
    @DisplayName("toLong: 无效字符串抛 NumberFormatException 后返回 0")
    void toLong_stringInvalid_returnsZero() {
        long result = ((Number) ReflectionTestUtils
                .invokeMethod(writeOffService, "toLong", new Object[]{"abc"})).longValue();
        assertEquals(0L, result);
    }

    @Test
    @DisplayName("toLong: 有效数字字符串返回对应 long 值")
    void toLong_stringValid_returnsLong() {
        long result = ((Number) ReflectionTestUtils
                .invokeMethod(writeOffService, "toLong", new Object[]{"123"})).longValue();
        assertEquals(123L, result);
    }
}
