package com.lianshengtong.common.util;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.exception.GlobalExceptionHandler;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.sharding.ShardingRouter;
import com.lianshengtong.common.utils.EvidenceHashUtil;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LSC Common P3 核心类单元测试")
@ExtendWith(MockitoExtension.class)
class CommonP3Test {

    // ==================== SnowflakeIdUtil 测试 ====================

    private SnowflakeIdUtil createInstance(long workerId, long datacenterId) throws Exception {
        Constructor<?> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        return (SnowflakeIdUtil) ctor.newInstance(workerId, datacenterId);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 workerId=0 边界值合法")
    void snowflake_constructor_workerIdZero_valid() {
        assertDoesNotThrow(() -> createInstance(0L, 1L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 workerId=31 边界值合法")
    void snowflake_constructor_workerIdMax_valid() {
        assertDoesNotThrow(() -> createInstance(31L, 1L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 datacenterId=0 边界值合法")
    void snowflake_constructor_datacenterIdZero_valid() {
        assertDoesNotThrow(() -> createInstance(1L, 0L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 datacenterId=31 边界值合法")
    void snowflake_constructor_datacenterIdMax_valid() {
        assertDoesNotThrow(() -> createInstance(1L, 31L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 workerId 超出范围抛异常")
    void snowflake_constructor_workerIdTooLarge_throws() {
        assertThrows(Exception.class, () -> createInstance(32L, 1L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 workerId 负数抛异常")
    void snowflake_constructor_workerIdNegative_throws() {
        assertThrows(Exception.class, () -> createInstance(-1L, 1L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 datacenterId 超出范围抛异常")
    void snowflake_constructor_datacenterIdTooLarge_throws() {
        assertThrows(Exception.class, () -> createInstance(1L, 32L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 构造函数 datacenterId 负数抛异常")
    void snowflake_constructor_datacenterIdNegative_throws() {
        assertThrows(Exception.class, () -> createInstance(1L, -1L));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: nextId 生成唯一ID")
    void snowflake_nextId_generatesUniqueIds() throws Exception {
        SnowflakeIdUtil instance = createInstance(1L, 1L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(instance.nextId());
        }
        assertEquals(100, ids.size());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: nextId 不同实例生成唯一ID")
    void snowflake_nextId_multipleInstances_unique() throws Exception {
        SnowflakeIdUtil inst1 = createInstance(1L, 1L);
        SnowflakeIdUtil inst2 = createInstance(2L, 2L);

        long id1 = inst1.nextId();
        long id2 = inst2.nextId();

        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 静态 id() 返回有效ID")
    void snowflake_staticId_returnsValidId() {
        long id = SnowflakeIdUtil.id();
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 静态 id() 连续调用返回唯一ID")
    void snowflake_staticId_unique() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(SnowflakeIdUtil.id());
        }
        assertEquals(100, ids.size());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: getInstance 返回单例")
    void snowflake_getInstance_returnsSingleton() {
        SnowflakeIdUtil inst1 = SnowflakeIdUtil.getInstance();
        SnowflakeIdUtil inst2 = SnowflakeIdUtil.getInstance();
        assertSame(inst1, inst2);
        assertNotNull(inst1);
    }

    // ==================== EvidenceHashUtil 测试 ====================

    @Test
    @DisplayName("EvidenceHashUtil: serialize null 返回 'null'")
    void evidenceHash_serializeNull_returnsNullString() {
        assertEquals("null", EvidenceHashUtil.serialize(null));
    }

    @Test
    @DisplayName("EvidenceHashUtil: serialize String 返回字符串本身")
    void evidenceHash_serializeString_returnsItself() {
        assertEquals("hello", EvidenceHashUtil.serialize("hello"));
    }

    @Test
    @DisplayName("EvidenceHashUtil: serialize Number 返回 toString")
    void evidenceHash_serializeNumber_returnsToString() {
        assertEquals("123", EvidenceHashUtil.serialize(123));
        assertEquals("3.14", EvidenceHashUtil.serialize(3.14));
    }

    @Test
    @DisplayName("EvidenceHashUtil: serialize Boolean 返回 toString")
    void evidenceHash_serializeBoolean_returnsToString() {
        assertEquals("true", EvidenceHashUtil.serialize(true));
        assertEquals("false", EvidenceHashUtil.serialize(false));
    }

    @Test
    @DisplayName("EvidenceHashUtil: serialize Map 使用 WriteNulls 序列化")
    void evidenceHash_serializeMap_serializesWithWriteNulls() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("value", null);

        String serialized = EvidenceHashUtil.serialize(map);

        assertNotNull(serialized);
        assertTrue(serialized.contains("\"name\":\"test\""));
        assertTrue(serialized.contains("\"value\":null"));
    }

    @Test
    @DisplayName("EvidenceHashUtil: sha256Hex 对同一对象返回一致哈希")
    void evidenceHash_sha256Hex_sameObject_sameHash() {
        TestPOJO pojo = new TestPOJO("Alice", new BigDecimal("100.50"),
                LocalDate.of(2026, 1, 15), 10, true);

        String hash1 = EvidenceHashUtil.sha256Hex(pojo);
        String hash2 = EvidenceHashUtil.sha256Hex(pojo);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("EvidenceHashUtil: sha256Hex 对不同对象返回不同哈希")
    void evidenceHash_sha256Hex_differentObjects_differentHash() {
        TestPOJO pojo1 = new TestPOJO("Alice", new BigDecimal("100.50"),
                LocalDate.of(2026, 1, 15), 10, true);
        TestPOJO pojo2 = new TestPOJO("Bob", new BigDecimal("200.50"),
                LocalDate.of(2026, 2, 20), 20, false);

        String hash1 = EvidenceHashUtil.sha256Hex(pojo1);
        String hash2 = EvidenceHashUtil.sha256Hex(pojo2);

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("EvidenceHashUtil: sha256Hex(String) 直接哈希字符串")
    void evidenceHash_sha256HexString_directHash() {
        String hash1 = EvidenceHashUtil.sha256Hex("test");
        String hash2 = EvidenceHashUtil.sha256Hex("test");

        assertEquals(hash1, hash2);
        assertNotNull(hash1);
    }

    @Test
    @DisplayName("EvidenceHashUtil: serialize POJO 字段按字母排序")
    void evidenceHash_serializePOJO_fieldsSorted() {
        TestPOJO pojo = new TestPOJO("Alice", new BigDecimal("100.50"),
                LocalDate.of(2026, 1, 15), 10, true);

        String serialized = EvidenceHashUtil.serialize(pojo);

        assertNotNull(serialized);
        assertTrue(serialized.contains("\"active\":true"));
        assertTrue(serialized.contains("\"count\":10"));
        assertTrue(serialized.contains("\"date\":\"2026-01-15\""));
        assertTrue(serialized.contains("\"name\":\"Alice\""));
    }

    @Test
    @DisplayName("EvidenceHashUtil: formatValue BigDecimal 保留两位小数")
    void evidenceHash_formatValue_bigDecimalScale() {
        TestPOJO pojo = new TestPOJO("test", new BigDecimal("100.5"),
                LocalDate.of(2026, 1, 1), 1, false);

        String serialized = EvidenceHashUtil.serialize(pojo);

        assertTrue(serialized.contains("\"amount\":100.50"));
    }

    @Test
    @DisplayName("EvidenceHashUtil: formatValue BigDecimal 四舍五入")
    void evidenceHash_formatValue_bigDecimalRounding() {
        TestPOJO pojo = new TestPOJO("test", new BigDecimal("100.555"),
                LocalDate.of(2026, 1, 1), 1, false);

        String serialized = EvidenceHashUtil.serialize(pojo);

        assertTrue(serialized.contains("\"amount\":100.56"));
    }

    @Test
    @DisplayName("EvidenceHashUtil: merkleRoot 空列表返回 sha256(\"\")")
    void evidenceHash_merkleRoot_emptyList_returnsSha256OfEmpty() {
        String result = EvidenceHashUtil.merkleRoot(Collections.emptyList());
        String expected = EvidenceHashUtil.sha256Hex("");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("EvidenceHashUtil: merkleRoot null 返回 sha256(\"\")")
    void evidenceHash_merkleRoot_null_returnsSha256OfEmpty() {
        String result = EvidenceHashUtil.merkleRoot(null);
        String expected = EvidenceHashUtil.sha256Hex("");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("EvidenceHashUtil: merkleRoot 单元素返回自身哈希")
    void evidenceHash_merkleRoot_singleElement_returnsItsHash() {
        String hash = EvidenceHashUtil.sha256Hex("hello");
        String result = EvidenceHashUtil.merkleRoot(Collections.singletonList(hash));

        assertEquals(hash, result);
    }

    @Test
    @DisplayName("EvidenceHashUtil: merkleRoot 双元素返回 hash(left+right)")
    void evidenceHash_merkleRoot_twoElements_hashOfLeftPlusRight() {
        String left = EvidenceHashUtil.sha256Hex("hello");
        String right = EvidenceHashUtil.sha256Hex("world");

        String result = EvidenceHashUtil.merkleRoot(Arrays.asList(left, right));

        String expected = EvidenceHashUtil.sha256Hex(left + right);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("EvidenceHashUtil: merkleRoot 三元素奇数补位")
    void evidenceHash_merkleRoot_threeElements_oddPadding() {
        String h1 = EvidenceHashUtil.sha256Hex("a");
        String h2 = EvidenceHashUtil.sha256Hex("b");
        String h3 = EvidenceHashUtil.sha256Hex("c");

        String result = EvidenceHashUtil.merkleRoot(Arrays.asList(h1, h2, h3));

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("EvidenceHashUtil: sha256Hex 对空字符串哈希一致")
    void evidenceHash_sha256Hex_emptyString_consistent() {
        String hash = EvidenceHashUtil.sha256Hex("");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    // ==================== ShardingRouter 测试 ====================

    @Test
    @DisplayName("ShardingRouter: 常量值验证")
    void shardingRouter_constants_verified() {
        assertEquals(32, ShardingRouter.SHARDING_COUNT);
        assertEquals(8, ShardingRouter.DB_COUNT);
        assertEquals(4, ShardingRouter.TABLES_PER_DB);
    }

    @Test
    @DisplayName("ShardingRouter: getDbIndex userId=0 返回0")
    void shardingRouter_getDbIndex_zero_returns0() {
        assertEquals(0, ShardingRouter.getDbIndex(0L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbIndex userId=3 返回0")
    void shardingRouter_getDbIndex_3_returns0() {
        assertEquals(0, ShardingRouter.getDbIndex(3L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbIndex userId=4 返回1")
    void shardingRouter_getDbIndex_4_returns1() {
        assertEquals(1, ShardingRouter.getDbIndex(4L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbIndex userId=7 返回1")
    void shardingRouter_getDbIndex_7_returns1() {
        assertEquals(1, ShardingRouter.getDbIndex(7L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbIndex userId=31 返回7")
    void shardingRouter_getDbIndex_31_returns7() {
        assertEquals(7, ShardingRouter.getDbIndex(31L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbIndex userId=32 返回0")
    void shardingRouter_getDbIndex_32_returns0() {
        assertEquals(0, ShardingRouter.getDbIndex(32L));
    }

    @Test
    @DisplayName("ShardingRouter: getTableIndex userId=0 返回0")
    void shardingRouter_getTableIndex_zero_returns0() {
        assertEquals(0, ShardingRouter.getTableIndex(0L));
    }

    @Test
    @DisplayName("ShardingRouter: getTableIndex userId=3 返回3")
    void shardingRouter_getTableIndex_3_returns3() {
        assertEquals(3, ShardingRouter.getTableIndex(3L));
    }

    @Test
    @DisplayName("ShardingRouter: getTableIndex userId=4 返回0")
    void shardingRouter_getTableIndex_4_returns0() {
        assertEquals(0, ShardingRouter.getTableIndex(4L));
    }

    @Test
    @DisplayName("ShardingRouter: getTableIndex userId=31 返回3")
    void shardingRouter_getTableIndex_31_returns3() {
        assertEquals(3, ShardingRouter.getTableIndex(31L));
    }

    @Test
    @DisplayName("ShardingRouter: getTableIndex userId=63 返回3")
    void shardingRouter_getTableIndex_63_returns3() {
        assertEquals(3, ShardingRouter.getTableIndex(63L));
    }

    @Test
    @DisplayName("ShardingRouter: getDbName 返回 base_index 格式")
    void shardingRouter_getDbName_correctFormat() {
        assertEquals("order_0", ShardingRouter.getDbName(0L, "order"));
        assertEquals("order_7", ShardingRouter.getDbName(31L, "order"));
    }

    @Test
    @DisplayName("ShardingRouter: getTableName 返回 base_index 格式")
    void shardingRouter_getTableName_correctFormat() {
        assertEquals("order_0", ShardingRouter.getTableName(0L, "order"));
        assertEquals("order_3", ShardingRouter.getTableName(31L, "order"));
    }

    @Test
    @DisplayName("ShardingRouter: 负数userId -1 路由结果")
    void shardingRouter_negativeUserId_minus1_routing() {
        int dbIndex = ShardingRouter.getDbIndex(-1L);
        int tableIndex = ShardingRouter.getTableIndex(-1L);

        assertEquals(0, dbIndex);
        assertEquals(-1, tableIndex);
    }

    @Test
    @DisplayName("ShardingRouter: 负数userId -32 整除路由有效")
    void shardingRouter_negativeUserId_minus32_routing() {
        int dbIndex = ShardingRouter.getDbIndex(-32L);
        int tableIndex = ShardingRouter.getTableIndex(-32L);

        assertEquals(0, dbIndex);
        assertEquals(0, tableIndex);
    }

    @Test
    @DisplayName("ShardingRouter: userId=63 正确路由")
    void shardingRouter_userId63_correctRouting() {
        assertEquals(7, ShardingRouter.getDbIndex(63L));
        assertEquals(3, ShardingRouter.getTableIndex(63L));
    }

    // ==================== BizException 测试 ====================

    @Test
    @DisplayName("BizException: message-only 构造器 code=500")
    void bizException_messageOnly_code500() {
        BizException ex = new BizException("出错了");
        assertEquals(500, ex.getCode());
        assertEquals("出错了", ex.getMessage());
    }

    @Test
    @DisplayName("BizException: code+message 构造器返回正确code")
    void bizException_codeAndMessage_correctCode() {
        BizException ex = new BizException(400, "参数错误");
        assertEquals(400, ex.getCode());
        assertEquals("参数错误", ex.getMessage());
    }

    @Test
    @DisplayName("BizException: ResultCode 构造器 code来自ResultCode")
    void bizException_resultCode_codeFromResultCode() {
        BizException ex = new BizException(ResultCode.PARAM_ERROR);
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals(ResultCode.PARAM_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("BizException: ResultCode+customMessage 自定义消息")
    void bizException_resultCodeWithCustomMessage_customMessage() {
        BizException ex = new BizException(ResultCode.SYSTEM_ERROR, "自定义系统错误");
        assertEquals(ResultCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertEquals("自定义系统错误", ex.getMessage());
    }

    @Test
    @DisplayName("BizException: SYSTEM_ERROR ResultCode code=500")
    void bizException_systemError_code500() {
        BizException ex = new BizException(ResultCode.SYSTEM_ERROR);
        assertEquals(500, ex.getCode());
    }

    @Test
    @DisplayName("BizException: IDEMPOTENT_DUPLICATE code=1001")
    void bizException_idempotentDuplicate_code1001() {
        BizException ex = new BizException(ResultCode.IDEMPOTENT_DUPLICATE);
        assertEquals(1001, ex.getCode());
    }

    // ==================== GlobalExceptionHandler 测试 ====================

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest mockRequest;

    @Test
    @DisplayName("GlobalExceptionHandler: handleBizException 返回 R.fail(code, message)")
    void globalExceptionHandler_handleBizException_returnsRfail() {
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        BizException ex = new BizException(400, "参数错误");

        R<Void> result = handler.handleBizException(ex, mockRequest);

        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMessage());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleBizException 使用 ResultCode")
    void globalExceptionHandler_handleBizException_withResultCode() {
        when(mockRequest.getRequestURI()).thenReturn("/api/order");
        BizException ex = new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);

        R<Void> result = handler.handleBizException(ex, mockRequest);

        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), result.getCode());
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getMessage(), result.getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleValidException MethodArgumentNotValidException 返回400")
    void globalExceptionHandler_handleValidException_returns400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        FieldError fieldError = new FieldError("user", "name", null, false, null, null, "不能为空");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        R<Void> result = handler.handleValidException(ex);

        assertEquals(400, result.getCode());
        assertEquals("不能为空", result.getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleValidException 多个错误消息拼接")
    void globalExceptionHandler_handleValidException_multipleErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        FieldError error1 = new FieldError("user", "name", null, false, null, null, "不能为空");
        FieldError error2 = new FieldError("user", "email", null, false, null, null, "格式错误");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(error1, error2));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        R<Void> result = handler.handleValidException(ex);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("不能为空"));
        assertTrue(result.getMessage().contains("格式错误"));
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleBindException 返回400")
    void globalExceptionHandler_handleBindException_returns400() {
        BindException ex = mock(BindException.class);
        FieldError fieldError = new FieldError("form", "field", null, false, null, null, "绑定错误");
        when(ex.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        R<Void> result = handler.handleBindException(ex);

        assertEquals(400, result.getCode());
        assertEquals("绑定错误", result.getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleConstraintViolationException 返回400")
    @SuppressWarnings("unchecked")
    void globalExceptionHandler_handleConstraintViolationException_returns400() {
        ConstraintViolation<String> cv = mock(ConstraintViolation.class);
        when(cv.getMessage()).thenReturn("参数不能为空");
        ConstraintViolationException ex = new ConstraintViolationException("校验失败", java.util.Set.of(cv));

        R<Void> result = handler.handleConstraintViolationException(ex);

        assertEquals(400, result.getCode());
        assertEquals("参数不能为空", result.getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleConstraintViolationException 多个违规拼接")
    @SuppressWarnings("unchecked")
    void globalExceptionHandler_handleConstraintViolationException_multipleViolations() {
        ConstraintViolation<String> cv1 = mock(ConstraintViolation.class);
        when(cv1.getMessage()).thenReturn("不能为空");
        ConstraintViolation<String> cv2 = mock(ConstraintViolation.class);
        when(cv2.getMessage()).thenReturn("长度超限");
        ConstraintViolationException ex = new ConstraintViolationException("校验失败", java.util.Set.of(cv1, cv2));

        R<Void> result = handler.handleConstraintViolationException(ex);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("不能为空"));
        assertTrue(result.getMessage().contains("长度超限"));
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleIllegalArgument 返回400和固定消息")
    void globalExceptionHandler_handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("非法参数");

        R<Void> result = handler.handleIllegalArgument(ex);

        assertEquals(400, result.getCode());
        assertEquals("请求参数不合法", result.getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: handleException 返回500和固定消息")
    void globalExceptionHandler_handleException_returns500() {
        Exception ex = new RuntimeException("系统异常");

        R<Void> result = handler.handleException(ex, mockRequest);

        assertEquals(500, result.getCode());
        assertEquals("系统错误，请稍后重试", result.getMessage());
    }

    // ==================== RedisKeyPrefix 测试 ====================

    @Test
    @DisplayName("RedisKeyPrefix: APP_PREFIX 常量为 lsc")
    void redisKeyPrefix_appPrefix_equalsLsc() {
        assertEquals("lsc", RedisKeyPrefix.APP_PREFIX);
    }

    @Test
    @DisplayName("RedisKeyPrefix: TOKEN_BLACKLIST 常量格式正确")
    void redisKeyPrefix_tokenBlacklist_formatVerified() {
        assertEquals("lsc:auth:token:blacklist:", RedisKeyPrefix.TOKEN_BLACKLIST);
    }

    @Test
    @DisplayName("RedisKeyPrefix: USER_TOKEN 常量格式正确")
    void redisKeyPrefix_userToken_formatVerified() {
        assertEquals("lsc:auth:user:token:", RedisKeyPrefix.USER_TOKEN);
    }

    @Test
    @DisplayName("RedisKeyPrefix: ADMIN_TOKEN 常量格式正确")
    void redisKeyPrefix_adminToken_formatVerified() {
        assertEquals("lsc:auth:admin:token:", RedisKeyPrefix.ADMIN_TOKEN);
    }

    @Test
    @DisplayName("RedisKeyPrefix: DISTRIBUTED_LOCK 常量格式正确")
    void redisKeyPrefix_distributedLock_formatVerified() {
        assertEquals("lsc:lock:", RedisKeyPrefix.DISTRIBUTED_LOCK);
    }

    @Test
    @DisplayName("RedisKeyPrefix: IDEMPOTENT 常量格式正确")
    void redisKeyPrefix_idempotent_formatVerified() {
        assertEquals("lsc:idempotent:", RedisKeyPrefix.IDEMPOTENT);
    }

    @Test
    @DisplayName("RedisKeyPrefix: LEDGER_BALANCE 常量格式正确")
    void redisKeyPrefix_ledgerBalance_formatVerified() {
        assertEquals("lsc:ledger:balance:", RedisKeyPrefix.LEDGER_BALANCE);
    }

    @Test
    @DisplayName("RedisKeyPrefix: ORDER_CACHE 常量格式正确")
    void redisKeyPrefix_orderCache_formatVerified() {
        assertEquals("lsc:order:cache:", RedisKeyPrefix.ORDER_CACHE);
    }

    @Test
    @DisplayName("RedisKeyPrefix: key(module, type, parts) 构建正确key")
    void redisKeyPrefix_keyMethod_buildsCorrectKey() {
        String key = RedisKeyPrefix.key("auth", "token", "user123");
        assertEquals("lsc:auth:token:user123", key);
    }

    @Test
    @DisplayName("RedisKeyPrefix: key(module, type) 无parts时使用varargs版本")
    void redisKeyPrefix_keyMethod_noParts_buildsCorrectKey() {
        String key = RedisKeyPrefix.key("lock", "leader", new String[0]);
        assertEquals("lsc:lock:leader", key);
    }

    @Test
    @DisplayName("RedisKeyPrefix: key(prefix, identifier) 前缀拼接")
    void redisKeyPrefix_keyMethod_prefixAndIdentifier() {
        String key = RedisKeyPrefix.key(RedisKeyPrefix.USER_TOKEN, "user123");
        assertEquals("lsc:auth:user:token:user123", key);
    }

    @Test
    @DisplayName("RedisKeyPrefix: key(module, type, parts) 跳过null和空字符串")
    void redisKeyPrefix_keyMethod_skipNullAndEmpty() {
        String key = RedisKeyPrefix.key("module", "type", "id1", null, "", "id2");
        assertEquals("lsc:module:type:id1:id2", key);
    }

    @Test
    @DisplayName("RedisKeyPrefix: scanPatterns 返回 prefix+* 数组")
    void redisKeyPrefix_scanPatterns_returnsCorrectArray() {
        String[] patterns = RedisKeyPrefix.scanPatterns("lsc:auth:user:token:");
        assertNotNull(patterns);
        assertEquals(1, patterns.length);
        assertEquals("lsc:auth:user:token:*", patterns[0]);
    }

    @Test
    @DisplayName("RedisKeyPrefix: cleanIdentifier 去除冒号和空格")
    void redisKeyPrefix_cleanIdentifier_handlesSpecialChars() {
        assertEquals("user_123", RedisKeyPrefix.cleanIdentifier("user:123"));
        assertEquals("user_name", RedisKeyPrefix.cleanIdentifier("user name"));
        assertEquals("user_id", RedisKeyPrefix.cleanIdentifier("user: id"));
    }

    @Test
    @DisplayName("RedisKeyPrefix: cleanIdentifier null 返回空字符串")
    void redisKeyPrefix_cleanIdentifier_null_returnsEmpty() {
        assertEquals("", RedisKeyPrefix.cleanIdentifier(null));
    }

    @Test
    @DisplayName("RedisKeyPrefix: MERCHANT_STATS 常量格式正确")
    void redisKeyPrefix_merchantStats_formatVerified() {
        assertEquals("lsc:merchant:stats:", RedisKeyPrefix.MERCHANT_STATS);
    }

    @Test
    @DisplayName("RedisKeyPrefix: B2B_ORDER 常量格式正确")
    void redisKeyPrefix_b2bOrder_formatVerified() {
        assertEquals("lsc:b2b:order:", RedisKeyPrefix.B2B_ORDER);
    }

    // ==================== 内部测试 POJO ====================

    public static class TestPOJO {
        private String name;
        private BigDecimal amount;
        private LocalDate date;
        private int count;
        private boolean active;

        public TestPOJO() {
        }

        public TestPOJO(String name, BigDecimal amount, LocalDate date, int count, boolean active) {
            this.name = name;
            this.amount = amount;
            this.date = date;
            this.count = count;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}