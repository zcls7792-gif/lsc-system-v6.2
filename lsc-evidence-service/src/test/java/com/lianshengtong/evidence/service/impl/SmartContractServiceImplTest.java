package com.lianshengtong.evidence.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.config.EvidenceCaffeineCache;
import com.lianshengtong.evidence.service.SmartContractService;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
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
 * SmartContractServiceImpl 单元测试（优化版）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("智能合约服务优化版测试")
class SmartContractServiceImplTest {

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
    @DisplayName("queryBlockNumber - 缓存命中直接返回，不发起RPC")
    void testQueryBlockNumber_CacheHit() throws IOException {
        String txHash = "0xabc123";
        cache.put("block:" + txHash, 100L, 60_000L);

        Long blockNumber = service.queryBlockNumber(txHash);

        assertEquals(100L, blockNumber);
        assertEquals(1, service.getCacheHitRate() > 0 ? 1 : 0);
        verify(httpClient, never()).newCall(any());
    }

    @Test
    @DisplayName("queryBlockNumber - 缓存miss时发起RPC并写入缓存")
    void testQueryBlockNumber_CacheMiss() throws IOException {
        String txHash = "0xabc456";
        String jsonResp = "{\"jsonrpc\":\"2.0\",\"result\":{\"blockNumber\":\"0x64\"}}";

        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn(jsonResp);

        Long blockNumber = service.queryBlockNumber(txHash);

        assertEquals(100L, blockNumber);
        verify(httpClient).newCall(any());
        // 验证缓存已写入
        Long cached = cache.get("block:" + txHash);
        assertEquals(100L, cached);
    }

    @Test
    @DisplayName("queryBlockNumber - RPC返回null时返回null")
    void testQueryBlockNumber_RpcReturnsNull() throws IOException {
        String txHash = "0xabc789";

        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":null}");

        Long blockNumber = service.queryBlockNumber(txHash);

        assertNull(blockNumber);
    }

    @Test
    @DisplayName("queryBlockNumberWithRetry - 首次成功直接返回")
    void testQueryBlockNumberWithRetry_FirstTrySuccess() throws IOException {
        String txHash = "0xpending1";
        cache.put("block:" + txHash, 200L, 60_000L);

        Long result = service.queryBlockNumberWithRetry(txHash, 3);

        assertEquals(200L, result);
    }

    @Test
    @DisplayName("queryBlockNumberWithRetry - 带重试机制")
    void testQueryBlockNumberWithRetry_WithRetry() throws IOException {
        String txHash = "0xpending2";
        // 首次和第二次返回null，第三次成功
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string())
                .thenReturn("{\"jsonrpc\":\"2.0\",\"result\":null}")
                .thenReturn("{\"jsonrpc\":\"2.0\",\"result\":null}")
                .thenReturn("{\"jsonrpc\":\"2.0\",\"result\":{\"blockNumber\":\"0x12c\"}}");

        Long result = service.queryBlockNumberWithRetry(txHash, 3);

        assertEquals(300L, result);
    }

    @Test
    @DisplayName("queryBlockNumberWithRetry - 全部重试失败返回null")
    void testQueryBlockNumberWithRetry_AllFail() throws IOException {
        String txHash = "0xpending3";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(500);

        Long result = service.queryBlockNumberWithRetry(txHash, 3);

        assertNull(result);
    }

    @Test
    @DisplayName("writeHash - 成功写入缓存txHash")
    void testWriteHash_Success() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xtxhash123\"}");

        String txHash = service.writeHash("hash1", "biz1");

        assertEquals("0xtxhash123", txHash);
        // 验证txHash已缓存
        String cachedTxHash = cache.get("tx:hash1:biz1");
        assertEquals("0xtxhash123", cachedTxHash);
    }

    @Test
    @DisplayName("writeHash - RPC失败抛异常")
    void testWriteHash_RpcFails() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("连接超时"));

        assertThrows(RuntimeException.class, () -> service.writeHash("hash1", "biz1"));
    }

    @Test
    @DisplayName("queryByHash - 缓存命中直接返回")
    void testQueryByHash_CacheHit() {
        String hash = "0xverify_hash_1";
        cache.put("verify:" + hash, true, 30_000L);

        String result = service.queryByHash(hash);

        assertEquals("VERIFIED", result);
        assertEquals(100L, service.getCacheHitRate()); // 1 hit / 1 total
    }

    @Test
    @DisplayName("queryByHash - 缓存miss时发起RPC并缓存结果")
    void testQueryByHash_CacheMiss() throws IOException {
        String hash = "0xverify_hash_2";
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xverified\"}");

        String result = service.queryByHash(hash);

        assertEquals("0xverified", result);
        // 验证已缓存
        Boolean cached = cache.get("verify:" + hash);
        assertEquals(true, cached);
    }

    @Test
    @DisplayName("batchWriteHash - 批量写入全部成功")
    void testBatchWriteHash_AllSuccess() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xtxhash\"}");

        List<String> hashes = List.of("hash1", "hash2", "hash3");
        List<String> bizIds = List.of("biz1", "biz2", "biz3");

        List<SmartContractServiceImpl.BatchWriteResult> results = service.batchWriteHash(hashes, bizIds);

        assertEquals(3, results.size());
        for (SmartContractServiceImpl.BatchWriteResult r : results) {
            assertTrue(r.isSuccess());
            assertNotNull(r.getTxHash());
        }
    }

    @Test
    @DisplayName("batchWriteHash - 部分成功部分失败")
    void testBatchWriteHash_PartialFail() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute())
                .thenReturn(response)
                .thenThrow(new IOException("RPC失败"))
                .thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"0xtxhash\"}");

        List<String> hashes = List.of("hash1", "hash2", "hash3");
        List<String> bizIds = List.of("biz1", "biz2", "biz3");

        List<SmartContractServiceImpl.BatchWriteResult> results = service.batchWriteHash(hashes, bizIds);

        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertNotNull(results.get(1).getError());
        assertTrue(results.get(2).isSuccess());
    }

    @Test
    @DisplayName("batchWriteHash - 空列表返回空结果")
    void testBatchWriteHash_EmptyList() {
        List<SmartContractServiceImpl.BatchWriteResult> results = service.batchWriteHash(null, null);
        assertTrue(results.isEmpty());

        results = service.batchWriteHash(List.of(), List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("getCacheHitRate - 命中率统计正确")
    void testGetCacheHitRate() throws IOException {
        cache.put("block:tx1", 100L, 60_000L);
        // 1次缓存命中
        service.queryBlockNumber("tx1");
        // 1次缓存miss
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":{\"blockNumber\":\"0x1\"}}");
        service.queryBlockNumber("tx2");

        long hitRate = service.getCacheHitRate();
        assertTrue(hitRate > 0);
    }

    @Test
    @DisplayName("getAverageLatency - 平均延迟计算正确")
    void testGetAverageLatency() throws IOException {
        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"ok\"}");

        long avgBefore = service.getAverageLatency();
        service.writeHash("h1", "b1");
        service.writeHash("h2", "b2");
        long avgAfter = service.getAverageLatency();

        assertTrue(avgAfter >= avgBefore);
    }

    @Test
    @DisplayName("getTotalRpcCalls - RPC调用计数正确")
    void testGetTotalRpcCalls() throws IOException {
        long before = service.getTotalRpcCalls();

        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"jsonrpc\":\"2.0\",\"result\":\"ok\"}");

        service.writeHash("h1", "b1");
        service.queryByHash("q1");

        long after = service.getTotalRpcCalls();
        assertEquals(before + 2, after);
    }
}
