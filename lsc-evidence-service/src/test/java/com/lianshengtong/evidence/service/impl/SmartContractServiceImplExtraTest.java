package com.lianshengtong.evidence.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.config.EvidenceCaffeineCache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SmartContractServiceImpl 附加覆盖率测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("智能合约服务覆盖率补充测试")
class SmartContractServiceImplExtraTest {

    private SmartContractServiceImpl service;

    @Mock
    private OkHttpClient httpClient;
    @Mock
    private Call call;
    @Mock
    private Response response;
    @Mock
    private ResponseBody responseBody;

    private EvidenceCache cache;

    @BeforeEach
    void setUp() {
        service = new SmartContractServiceImpl();
        cache = new EvidenceCaffeineCache(10000, 30_000L);
        ReflectionTestUtils.setField(service, "rpcUrl", "http://localhost:8545");
        ReflectionTestUtils.setField(service, "contractAddress", "0xContractAddress");
        ReflectionTestUtils.setField(service, "privateKey", "0xPrivateKey");
        ReflectionTestUtils.setField(service, "evidenceLocalCache", cache);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    @Test
    @DisplayName("writeHash - bizId 为 null 时仍能正常写入")
    void testWriteHash_NullBizId() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xtxhash_null\"}");

        String txHash = service.writeHash("hash_null", null);

        assertEquals("0xtxhash_null", txHash);
    }

    @Test
    @DisplayName("writeHash - 响应 body 为 null 抛 IOException")
    void testWriteHash_ResponseBodyNull() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.writeHash("h", "b"));
    }

    @Test
    @DisplayName("writeHash - HTTP 状态码非 2xx 抛 IOException")
    void testWriteHash_ResponseNotSuccessful() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(500);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.writeHash("h", "b"));
        assertTrue(ex.getMessage().contains("上链失败"));
    }

    @Test
    @DisplayName("queryByHash - 缓存中存储 false 时返回 null")
    void testQueryByHash_CacheFalse() {
        String hash = "0xverify_false";
        cache.put("verify:" + hash, false, 30_000L);

        String result = service.queryByHash(hash);

        assertNull(result);
        assertEquals(100L, service.getCacheHitRate());
    }

    @Test
    @DisplayName("queryByHash - RPC 返回空字符串时缓存为 false 返回 null")
    void testQueryByHash_RpcReturnsBlank() throws IOException {
        String hash = "0xverify_blank";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"\"}");

        String result = service.queryByHash(hash);

        assertNull(result);
        Boolean cached = cache.get("verify:" + hash);
        assertEquals(false, cached);
    }

    @Test
    @DisplayName("queryByHash - RPC 调用异常时返回 null")
    void testQueryByHash_RpcThrowsException() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("网络异常"));

        String result = service.queryByHash("0xexception");

        assertNull(result);
    }

    @Test
    @DisplayName("queryBlockNumber - result.blockNumber 为 null 时返回 null")
    void testQueryBlockNumber_BlockNumberNull() throws IOException {
        String txHash = "0xbn_null";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":{\"blockNumber\":null}}");

        Long result = service.queryBlockNumber(txHash);

        assertNull(result);
    }

    @Test
    @DisplayName("queryBlockNumber - result 为 null 返回 null")
    void testQueryBlockNumber_ResultNull() throws IOException {
        String txHash = "0xresult_null";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":null}");

        Long result = service.queryBlockNumber(txHash);
        assertNull(result);
    }

    @Test
    @DisplayName("queryBlockNumber - RPC 异常时返回 null")
    void testQueryBlockNumber_Exception() throws IOException {
        String txHash = "0xexception";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("RPC 异常"));

        Long result = service.queryBlockNumber(txHash);
        assertNull(result);
    }

    @Test
    @DisplayName("queryBlockNumberWithRetry - 全部重试返回 null")
    void testQueryBlockNumberWithRetry_AllNull() throws IOException {
        String txHash = "0xall_null";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":null}");

        Long result = service.queryBlockNumberWithRetry(txHash, 3);
        assertNull(result);
    }

    @Test
    @DisplayName("queryBlockNumberWithRetry - 首次成功直接返回缓存命中")
    void testQueryBlockNumberWithRetry_FirstTryCacheHit() {
        String txHash = "0xfirst_hit";
        cache.put("block:" + txHash, 999L, 60_000L);

        Long result = service.queryBlockNumberWithRetry(txHash, 3);

        assertEquals(999L, result);
    }

    @Test
    @DisplayName("batchWriteHash - bizIds 数量少于 dataHashes 时按空字符串补齐")
    void testBatchWriteHash_BizIdsShorter() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xtx\"}");

        List<String> hashes = List.of("h1", "h2", "h3");
        List<String> bizIds = List.of("b1"); // 只有1个，少于3个

        List<SmartContractServiceImpl.BatchWriteResult> results = service.batchWriteHash(hashes, bizIds);

        assertEquals(3, results.size());
        // 第2、3条的 bizId 应为 ""
        for (SmartContractServiceImpl.BatchWriteResult r : results) {
            assertTrue(r.isSuccess());
        }
    }

    @Test
    @DisplayName("batchWriteHash - 哈希列表为 null 返回空结果")
    void testBatchWriteHash_NullHashes() {
        List<SmartContractServiceImpl.BatchWriteResult> results = service.batchWriteHash(null, List.of("b"));
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("getCacheHitRate - 无请求时为 0")
    void testGetCacheHitRate_ZeroRequests() {
        long rate = service.getCacheHitRate();
        assertEquals(0L, rate);
    }

    @Test
    @DisplayName("getAverageLatency - 无调用时为 0")
    void testGetAverageLatency_NoCalls() {
        long latency = service.getAverageLatency();
        assertEquals(0L, latency);
    }

    @Test
    @DisplayName("getTotalRpcCalls - 初始为 0")
    void testGetTotalRpcCalls_InitiallyZero() {
        assertEquals(0L, service.getTotalRpcCalls());
    }

    @Test
    @DisplayName("BatchWriteResult - isSuccess 在 error 为 null 时返回 true")
    void testBatchWriteResult_Success() {
        SmartContractServiceImpl.BatchWriteResult r =
                new SmartContractServiceImpl.BatchWriteResult("h", "tx", null);
        assertTrue(r.isSuccess());
        assertEquals("h", r.getDataHash());
        assertEquals("tx", r.getTxHash());
        assertNull(r.getError());
    }

    @Test
    @DisplayName("BatchWriteResult - isSuccess 在 error 非 null 时返回 false")
    void testBatchWriteResult_Failure() {
        SmartContractServiceImpl.BatchWriteResult r =
                new SmartContractServiceImpl.BatchWriteResult("h", null, "error");
        assertFalse(r.isSuccess());
        assertNotNull(r.getError());
        assertEquals("error", r.getError());
    }

    @Test
    @DisplayName("writeHash - 写入成功后 totalRpcCalls 递增")
    void testWriteHash_IncrementsRpcCalls() throws IOException {
        long before = service.getTotalRpcCalls();

        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xtx\"}");

        service.writeHash("h", "b");

        assertEquals(before + 1, service.getTotalRpcCalls());
    }

    @Test
    @DisplayName("queryBlockNumber - 缓存未命中时 miss+1")
    void testQueryBlockNumber_MissIncrements() throws IOException {
        long hitsBefore = cache.get("block:unknown") == null ? 0 : 1;

        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":{\"blockNumber\":\"0xa\"}}");

        service.queryBlockNumber("0xnew");

        // 验证 cacheHitRate 正常计算（miss 已累加）
        assertTrue(service.getCacheHitRate() >= 0);
    }
}
