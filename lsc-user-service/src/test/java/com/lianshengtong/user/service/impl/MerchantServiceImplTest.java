package com.lianshengtong.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.enums.MerchantPenaltyStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.user.dto.MerchantApplyDTO;
import com.lianshengtong.user.entity.MerchantExtension;
import com.lianshengtong.user.entity.StoreAddress;
import com.lianshengtong.user.mapper.MerchantExtensionMapper;
import com.lianshengtong.user.mapper.StoreAddressMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("商家服务单元测试")
class MerchantServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, MerchantExtension.class);
        TableInfoHelper.initTableInfo(assistant, StoreAddress.class);
    }

    @Mock
    private MerchantExtensionMapper merchantMapper;

    @Mock
    private StoreAddressMapper storeAddressMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MerchantServiceImpl merchantService;

    private static final int DAILY_ADDRESS_LIMIT = 3;
    private static final int INIT_CREDIT_SCORE = 100;
    private static final int INIT_DAILY_NH_LIMIT = 80;

    private MerchantExtension buildExtension(Long merchantId) {
        MerchantExtension ext = new MerchantExtension();
        ext.setMerchantId(merchantId);
        ext.setStoreName("测试店铺");
        ext.setBusinessLicense("91330100MA2XXXXXXX");
        ext.setCreditScore(INIT_CREDIT_SCORE);
        ext.setAiRiskScore(INIT_CREDIT_SCORE);
        ext.setDailyNhLimit(INIT_DAILY_NH_LIMIT);
        ext.setNhLimitLevel(0);
        ext.setPenaltyStatus(MerchantPenaltyStatusEnum.NORMAL.getCode());
        ext.setAuditStatus(0);
        ext.setAddressUpdateCount(0);
        ext.setProvince("浙江省");
        ext.setCity("杭州市");
        ext.setDistrict("西湖区");
        ext.setAddressDetail("文三路100号");
        ext.setContactPhone("13800138000");
        ext.setBusinessHours("09:00-22:00");
        return ext;
    }

    private MerchantApplyDTO buildDTO(Long merchantId) {
        MerchantApplyDTO dto = new MerchantApplyDTO();
        dto.setMerchantId(merchantId);
        dto.setBusinessLicense("91330100MA2XXXXXXX");
        dto.setBusinessLicenseImg("https://img.example.com/lic.jpg");
        dto.setStoreName("测试店铺");
        dto.setProvince("浙江省");
        dto.setCity("杭州市");
        dto.setDistrict("西湖区");
        dto.setAddressDetail("文三路100号");
        dto.setContactPhone("13800138000");
        dto.setBusinessHours("09:00-22:00");
        return dto;
    }

    private void mockRedisIncrement(Long count) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(count);
    }

    private void mockRedisGet(String val) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(val);
    }

    // ============== register 测试 ==============

    @Test
    @DisplayName("register: 首次注册成功，初始化信用分/核销额度")
    void register_firstTime_success() {
        MerchantApplyDTO dto = buildDTO(1L);
        when(merchantMapper.selectById(1L)).thenReturn(null);
        when(merchantMapper.insert(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.register(dto);

        assertNotNull(result);
        assertEquals(1L, result.getMerchantId());
        assertEquals(INIT_CREDIT_SCORE, result.getCreditScore());
        assertEquals(INIT_CREDIT_SCORE, result.getAiRiskScore());
        assertEquals(INIT_DAILY_NH_LIMIT, result.getDailyNhLimit());
        assertEquals(0, result.getNhLimitLevel());
        assertEquals(MerchantPenaltyStatusEnum.NORMAL.getCode(), result.getPenaltyStatus());
        assertEquals(0, result.getAuditStatus());
        assertEquals(0, result.getAddressUpdateCount());
        verify(merchantMapper).insert(any(MerchantExtension.class));
    }

    @Test
    @DisplayName("register: 重复注册(已提交营业执照)抛异常")
    void register_alreadySubmitted_throws() {
        MerchantApplyDTO dto = buildDTO(1L);
        MerchantExtension exist = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(exist);

        BizException ex = assertThrows(BizException.class, () -> merchantService.register(dto));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已提交"));
    }

    @Test
    @DisplayName("register: 重新注册(之前未提交营业执照)成功")
    void register_reRegister_success() {
        MerchantApplyDTO dto = buildDTO(1L);
        MerchantExtension exist = new MerchantExtension();
        exist.setMerchantId(1L);
        exist.setBusinessLicense(null);
        when(merchantMapper.selectById(1L)).thenReturn(exist);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.register(dto);

        assertNotNull(result);
        assertEquals(1L, result.getMerchantId());
        assertEquals(INIT_CREDIT_SCORE, result.getCreditScore());
        verify(merchantMapper).updateById(any(MerchantExtension.class));
    }

    // ============== audit 测试 ==============

    @Test
    @DisplayName("audit: 审核通过，信用分初始化")
    void audit_pass_success() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(null);
        ext.setDailyNhLimit(null);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.audit(1L, 1, "通过");

        assertNotNull(result);
        assertEquals(1, result.getAuditStatus());
        assertEquals(INIT_CREDIT_SCORE, result.getCreditScore());
        assertEquals(INIT_DAILY_NH_LIMIT, result.getDailyNhLimit());
    }

    @Test
    @DisplayName("audit: 审核拒绝")
    void audit_reject_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.audit(1L, 2, "证件模糊");

        assertNotNull(result);
        assertEquals(2, result.getAuditStatus());
    }

    @Test
    @DisplayName("audit: 非法审核状态抛异常")
    void audit_invalidStatus_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> merchantService.audit(1L, 3, "test"));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("audit: 审核通过已有信用分不覆盖")
    void audit_pass_existingCreditScore() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(80);
        ext.setDailyNhLimit(50);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.audit(1L, 1, "通过");

        assertEquals(80, result.getCreditScore());
        assertEquals(50, result.getDailyNhLimit());
    }

    // ============== updateCredit 测试 ==============

    @Test
    @DisplayName("updateCredit: 有效范围更新，联动处罚状态")
    void updateCredit_valid_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.updateCredit(1L, 70);

        assertNotNull(result);
        assertEquals(70, result.getCreditScore());
        assertEquals(MerchantPenaltyStatusEnum.LEVEL1.getCode(), result.getPenaltyStatus());
    }

    @Test
    @DisplayName("updateCredit: 信用分0-100边界测试")
    void updateCredit_boundary_valid() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result0 = merchantService.updateCredit(1L, 0);
        assertEquals(0, result0.getCreditScore());
        assertEquals(MerchantPenaltyStatusEnum.EXPELLED.getCode(), result0.getPenaltyStatus());

        MerchantExtension ext2 = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext2);
        MerchantExtension result100 = merchantService.updateCredit(1L, 100);
        assertEquals(100, result100.getCreditScore());
        assertEquals(MerchantPenaltyStatusEnum.NORMAL.getCode(), result100.getPenaltyStatus());
    }

    @Test
    @DisplayName("updateCredit: 超范围抛异常")
    void updateCredit_outOfRange_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> merchantService.updateCredit(1L, 101));
        assertEquals(400, ex.getCode());

        BizException ex2 = assertThrows(BizException.class,
                () -> merchantService.updateCredit(1L, -1));
        assertEquals(400, ex2.getCode());
    }

    @Test
    @DisplayName("updateCredit: 信用分60-79为一级处罚")
    void updateCredit_level1_penalty() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.updateCredit(1L, 60);
        assertEquals(MerchantPenaltyStatusEnum.LEVEL1.getCode(), result.getPenaltyStatus());
    }

    // ============== updateWriteOffLevel 测试 ==============

    @Test
    @DisplayName("updateWriteOffLevel: 有效范围更新")
    void updateWriteOffLevel_valid_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.updateWriteOffLevel(1L, 8);

        assertNotNull(result);
        assertEquals(8, result.getNhLimitLevel());
    }

    @Test
    @DisplayName("updateWriteOffLevel: 超范围抛异常")
    void updateWriteOffLevel_outOfRange_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> merchantService.updateWriteOffLevel(1L, 17));
        assertEquals(400, ex.getCode());

        BizException ex2 = assertThrows(BizException.class,
                () -> merchantService.updateWriteOffLevel(1L, -1));
        assertEquals(400, ex2.getCode());
    }

    // ============== updateAddress 测试 ==============

    @Test
    @DisplayName("updateAddress: 正常更新(首次计数)")
    void updateAddress_success_firstCount() {
        MerchantExtension ext = buildExtension(1L);
        ext.setAddressUpdateCount(0);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(1L);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantApplyDTO dto = buildDTO(1L);
        MerchantExtension result = merchantService.updateAddress(dto);

        assertNotNull(result);
        assertEquals("浙江省", result.getProvince());
        assertEquals(1, result.getAddressUpdateCount());
        verify(stringRedisTemplate).expire(anyString(), any());
    }

    @Test
    @DisplayName("updateAddress: 非首次计数不累加过期时间")
    void updateAddress_secondCount_noExpire() {
        MerchantExtension ext = buildExtension(1L);
        ext.setAddressUpdateCount(2);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(2L);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantApplyDTO dto = buildDTO(1L);
        MerchantExtension result = merchantService.updateAddress(dto);

        assertEquals(3, result.getAddressUpdateCount());
        verify(stringRedisTemplate, never()).expire(anyString(), any());
    }

    @Test
    @DisplayName("updateAddress: 超过每日限制抛异常")
    void updateAddress_overDailyLimit_throws() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(4L);

        MerchantApplyDTO dto = buildDTO(1L);
        BizException ex = assertThrows(BizException.class, () -> merchantService.updateAddress(dto));
        assertEquals(ResultCode.ADDRESS_DAILY_LIMIT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateAddress: 已达限制(刚好等于3)仍可通过")
    void updateAddress_atLimit_passes() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(3L);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantApplyDTO dto = buildDTO(1L);
        MerchantExtension result = merchantService.updateAddress(dto);

        assertNotNull(result);
    }

    // ============== getMerchantInfo 测试 ==============

    @Test
    @DisplayName("getMerchantInfo: 商家存在返回信息")
    void getMerchantInfo_exists_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);

        MerchantExtension result = merchantService.getMerchantInfo(1L);

        assertNotNull(result);
        assertEquals(1L, result.getMerchantId());
        assertEquals("测试店铺", result.getStoreName());
    }

    @Test
    @DisplayName("getMerchantInfo: 商家不存在抛异常")
    void getMerchantInfo_notFound_throws() {
        when(merchantMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.getMerchantInfo(999L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============== listMerchants 测试 ==============

    @Test
    @DisplayName("listMerchants: 多条件筛选成功")
    void listMerchants_withFilters_success() {
        MerchantExtension ext = buildExtension(1L);
        Page<MerchantExtension> page = new Page<>(1, 20);
        page.setRecords(List.of(ext));
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<MerchantExtension> result = merchantService.listMerchants(1, 20, "测试", 0, 60, 100);

        assertNotNull(result);
        assertFalse(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("listMerchants: 默认分页参数")
    void listMerchants_defaultPageSize() {
        Page<MerchantExtension> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<MerchantExtension> result = merchantService.listMerchants(null, null, null, null, null, null);

        assertNotNull(result);
    }

    // ============== auditList 测试 ==============

    @Test
    @DisplayName("auditList: 默认待审核列表")
    void auditList_defaultStatus_success() {
        Page<MerchantExtension> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<MerchantExtension> result = merchantService.auditList(1, 20, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("auditList: 指定审核状态列表")
    void auditList_withStatus_success() {
        Page<MerchantExtension> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<MerchantExtension> result = merchantService.auditList(1, 20, 1);

        assertNotNull(result);
    }

    // ============== getCreditDetail 测试 ==============

    @Test
    @DisplayName("getCreditDetail: 正常返回信用分明细")
    void getCreditDetail_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);

        Map<String, Object> result = merchantService.getCreditDetail(1L);

        assertNotNull(result);
        assertEquals(1L, result.get("merchantId"));
        assertEquals("测试店铺", result.get("storeName"));
        assertEquals(100, result.get("creditScore"));
        assertEquals(MerchantPenaltyStatusEnum.NORMAL.getCode(), result.get("penaltyStatus"));
        assertEquals("正常", result.get("penaltyDesc"));
    }

    @Test
    @DisplayName("getCreditDetail: 商家不存在抛异常")
    void getCreditDetail_notFound_throws() {
        when(merchantMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.getCreditDetail(1L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============== penalize 测试 ==============

    @Test
    @DisplayName("penalize: 一级处罚扣10分")
    void penalize_level1_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.penalize(1L, 1, "违规", null);

        assertEquals(1, result.getPenaltyStatus());
        assertEquals(90, result.getCreditScore());
    }

    @Test
    @DisplayName("penalize: 清退处罚信用分置0")
    void penalize_expelled_success() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(50);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.penalize(1L, 4, "严重违规", null);

        assertEquals(4, result.getPenaltyStatus());
        assertEquals(0, result.getCreditScore());
    }

    @Test
    @DisplayName("penalize: 非法处罚类型抛异常")
    void penalize_invalidType_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> merchantService.penalize(1L, 5, "test", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("penalize: 三级处罚扣30分，分数不低于0")
    void penalize_level3_clampToZero() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(20);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.penalize(1L, 3, "违规", null);

        assertEquals(3, result.getPenaltyStatus());
        assertEquals(0, result.getCreditScore());
    }

    // ============== adjustCredit 测试 ==============

    @Test
    @DisplayName("adjustCredit: 正调整信用分")
    void adjustCredit_positive_success() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(80);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.adjustCredit(1L, 10, "表现良好");

        assertEquals(90, result.getCreditScore());
        assertEquals(MerchantPenaltyStatusEnum.NORMAL.getCode(), result.getPenaltyStatus());
    }

    @Test
    @DisplayName("adjustCredit: 负调整信用分")
    void adjustCredit_negative_success() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(80);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.adjustCredit(1L, -30, "违规扣分");

        assertEquals(50, result.getCreditScore());
        assertEquals(MerchantPenaltyStatusEnum.LEVEL2.getCode(), result.getPenaltyStatus());
    }

    @Test
    @DisplayName("adjustCredit: 调整后超100分限幅")
    void adjustCredit_clampTo100() {
        MerchantExtension ext = buildExtension(1L);
        ext.setCreditScore(95);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        MerchantExtension result = merchantService.adjustCredit(1L, 20, "大幅加分");

        assertEquals(100, result.getCreditScore());
    }

    @Test
    @DisplayName("adjustCredit: delta为0抛异常")
    void adjustCredit_zeroDelta_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> merchantService.adjustCredit(1L, 0, "test"));
        assertEquals(400, ex.getCode());
    }

    // ============== updateStoreInfo 测试 ==============

    @Test
    @DisplayName("updateStoreInfo: 多字段更新成功")
    void updateStoreInfo_multiFields_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storeName", "新店铺名");
        params.put("contactPhone", "13900139000");
        params.put("businessHours", "10:00-23:00");

        MerchantExtension result = merchantService.updateStoreInfo(1L, params);

        assertEquals("新店铺名", result.getStoreName());
        assertEquals("13900139000", result.getContactPhone());
        assertEquals("10:00-23:00", result.getBusinessHours());
    }

    @Test
    @DisplayName("updateStoreInfo: 空参数抛异常")
    void updateStoreInfo_emptyParams_throws() {
        assertThrows(BizException.class,
                () -> merchantService.updateStoreInfo(1L, null));

        assertThrows(BizException.class,
                () -> merchantService.updateStoreInfo(1L, new HashMap<>()));
    }

    @Test
    @DisplayName("updateStoreInfo: 营业执照字段更新")
    void updateStoreInfo_businessLicense_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("businessLicense", "91330100NEWXXXX");
        params.put("businessLicenseImg", "https://img.example.com/new.jpg");

        MerchantExtension result = merchantService.updateStoreInfo(1L, params);

        assertEquals("91330100NEWXXXX", result.getBusinessLicense());
        assertEquals("https://img.example.com/new.jpg", result.getBusinessLicenseImg());
    }

    // ============== listStoreAddresses 测试 ==============

    @Test
    @DisplayName("listStoreAddresses: 商家存在返回地址列表")
    void listStoreAddresses_exists_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);

        StoreAddress addr = new StoreAddress();
        addr.setId(1L);
        addr.setMerchantId(1L);
        addr.setIsPrimary(1);
        addr.setProvince("浙江省");
        addr.setCity("杭州市");
        when(storeAddressMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(addr));

        List<StoreAddress> result = merchantService.listStoreAddresses(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listStoreAddresses: 商家不存在抛异常")
    void listStoreAddresses_notFound_throws() {
        when(merchantMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.listStoreAddresses(1L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ============== saveStoreAddress 测试 ==============

    @Test
    @DisplayName("saveStoreAddress: 新增首条地址自动设为主地址")
    void saveStoreAddress_new_firstBecomesPrimary() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(1L);
        when(storeAddressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(storeAddressMapper.insert(any(StoreAddress.class))).thenReturn(1);

        StoreAddress addr = new StoreAddress();
        addr.setMerchantId(1L);
        addr.setProvince("浙江省");
        addr.setCity("杭州市");
        addr.setAddressDetail("文三路100号");

        StoreAddress result = merchantService.saveStoreAddress(addr);

        assertEquals(1, result.getIsPrimary());
    }

    @Test
    @DisplayName("saveStoreAddress: 新增非首条地址不设为主")
    void saveStoreAddress_new_notFirstNotPrimary() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(1L);
        when(storeAddressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(storeAddressMapper.insert(any(StoreAddress.class))).thenReturn(1);

        StoreAddress addr = new StoreAddress();
        addr.setMerchantId(1L);
        addr.setProvince("浙江省");
        addr.setCity("杭州市");

        StoreAddress result = merchantService.saveStoreAddress(addr);

        assertEquals(0, result.getIsPrimary());
    }

    @Test
    @DisplayName("saveStoreAddress: 编辑地址成功")
    void saveStoreAddress_edit_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(1L);

        StoreAddress exist = new StoreAddress();
        exist.setId(1L);
        exist.setMerchantId(1L);
        exist.setIsPrimary(0);
        when(storeAddressMapper.selectById(1L)).thenReturn(exist);
        when(storeAddressMapper.updateById(any(StoreAddress.class))).thenReturn(1);

        StoreAddress addr = new StoreAddress();
        addr.setId(1L);
        addr.setMerchantId(1L);
        addr.setProvince("浙江省");

        StoreAddress result = merchantService.saveStoreAddress(addr);

        assertNotNull(result);
    }

    @Test
    @DisplayName("saveStoreAddress: 编辑非本商家地址抛异常")
    void saveStoreAddress_editForbidden_throws() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisIncrement(1L);

        StoreAddress exist = new StoreAddress();
        exist.setId(1L);
        exist.setMerchantId(999L);
        when(storeAddressMapper.selectById(1L)).thenReturn(exist);

        StoreAddress addr = new StoreAddress();
        addr.setId(1L);
        addr.setMerchantId(1L);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.saveStoreAddress(addr));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ============== deleteStoreAddress 测试 ==============

    @Test
    @DisplayName("deleteStoreAddress: 正常删除地址")
    void deleteStoreAddress_success() {
        StoreAddress exist = new StoreAddress();
        exist.setId(1L);
        exist.setMerchantId(1L);
        exist.setIsPrimary(0);
        when(storeAddressMapper.selectById(1L)).thenReturn(exist);
        when(storeAddressMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> merchantService.deleteStoreAddress(1L, 1L));
    }

    @Test
    @DisplayName("deleteStoreAddress: 删除主地址自动提升最近一条")
    void deleteStoreAddress_primary_autoPromote() {
        StoreAddress exist = new StoreAddress();
        exist.setId(1L);
        exist.setMerchantId(1L);
        exist.setIsPrimary(1);
        when(storeAddressMapper.selectById(1L)).thenReturn(exist);
        when(storeAddressMapper.deleteById(1L)).thenReturn(1);

        StoreAddress next = new StoreAddress();
        next.setId(2L);
        next.setMerchantId(1L);
        next.setIsPrimary(0);
        when(storeAddressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(next);

        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);

        assertDoesNotThrow(() -> merchantService.deleteStoreAddress(1L, 1L));
        verify(storeAddressMapper).updateById(any(StoreAddress.class));
    }

    @Test
    @DisplayName("deleteStoreAddress: 地址不存在抛异常")
    void deleteStoreAddress_notFound_throws() {
        when(storeAddressMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.deleteStoreAddress(1L, 1L));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ============== setPrimaryAddress 测试 ==============

    @Test
    @DisplayName("setPrimaryAddress: 正常设置主地址")
    void setPrimaryAddress_success() {
        StoreAddress exist = new StoreAddress();
        exist.setId(1L);
        exist.setMerchantId(1L);
        exist.setIsPrimary(0);
        exist.setProvince("浙江省");
        exist.setCity("杭州市");
        exist.setDistrict("西湖区");
        exist.setAddressDetail("文三路100号");
        exist.setLongitude(new BigDecimal("120.15"));
        exist.setLatitude(new BigDecimal("30.25"));
        when(storeAddressMapper.selectById(1L)).thenReturn(exist);
        when(storeAddressMapper.updateById(any(StoreAddress.class))).thenReturn(1);

        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        when(merchantMapper.updateById(any(MerchantExtension.class))).thenReturn(1);

        StoreAddress result = merchantService.setPrimaryAddress(1L, 1L);

        assertEquals(1, result.getIsPrimary());
    }

    @Test
    @DisplayName("setPrimaryAddress: 非法地址抛异常")
    void setPrimaryAddress_notFound_throws() {
        when(storeAddressMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> merchantService.setPrimaryAddress(1L, 1L));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ============== getAddressUpdateState 测试 ==============

    @Test
    @DisplayName("getAddressUpdateState: 正常返回状态")
    void getAddressUpdateState_success() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisGet("1");

        Map<String, Object> result = merchantService.getAddressUpdateState(1L);

        assertNotNull(result);
        assertEquals(1L, result.get("todayUpdatedCount"));
        assertEquals(DAILY_ADDRESS_LIMIT, result.get("dailyLimit"));
        assertEquals(2L, result.get("remaining"));
    }

    @Test
    @DisplayName("getAddressUpdateState: 无修改记录返回0")
    void getAddressUpdateState_noRecord_returnsZero() {
        MerchantExtension ext = buildExtension(1L);
        when(merchantMapper.selectById(1L)).thenReturn(ext);
        mockRedisGet(null);

        Map<String, Object> result = merchantService.getAddressUpdateState(1L);

        assertEquals(0L, result.get("todayUpdatedCount"));
        assertEquals(DAILY_ADDRESS_LIMIT, result.get("dailyLimit"));
        assertEquals(3L, result.get("remaining"));
    }

    // ============== updateLastNhDate 测试 ==============

    @Test
    @DisplayName("updateLastNhDate: 正常更新")
    void updateLastNhDate_success() {
        when(merchantMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertDoesNotThrow(() ->
                merchantService.updateLastNhDate(1L, LocalDate.of(2026, 8, 7)));
    }

    @Test
    @DisplayName("updateLastNhDate: 更新0行不抛异常仅记日志")
    void updateLastNhDate_zeroRows_noException() {
        when(merchantMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertDoesNotThrow(() ->
                merchantService.updateLastNhDate(1L, LocalDate.now()));
    }
}