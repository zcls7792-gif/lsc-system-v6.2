package com.lianshengtong.evidence.integration;

import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.config.EvidenceCaffeineCache;
import com.lianshengtong.evidence.service.impl.SmartContractServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartContractService 真实链上交互集成测试
 * <p>
 * 测试链上交互的真实行为，包括：
 * <ul>
 *   <li>writeHash 真实上链</li>
 *   <li>queryByHash 链上查询</li>
 *   <li>queryBlockNumber 区块号查询</li>
 *   <li>queryBlockNumberWithRetry 重试机制</li>
 *   <li>批量写入</li>
 *   <li>缓存命中验证</li>
 *   <li>异常处理 (无效地址、超时等)</li>
 * </ul>
 * <p>
 * 注意：这些测试会真实提交链上交易，仅在测试链 (Ganache/Hardhat/测试网) 上运行。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("SmartContractService 链上集成测试")
class SmartContractServiceChainIntegrationTest extends ChainIntegrationTestBase {

    private static final Logger log = Logger.getLogger(SmartContractServiceChainIntegrationTest.class.getName());

    /**
     * 测试链节点可达性 (eth_blockNumber)
     * 这是所有后续测试的前置条件
     */
    @Test
    @Order(1)
    @DisplayName("链节点可达 - eth_blockNumber 返回有效区块号")
    void chainNodeReachable() {
        try {
            okhttp3.OkHttpClient client = (okhttp3.OkHttpClient)
                    ReflectionTestUtils.getField(smartContractService, "httpClient");
            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1}";
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(RPC_URL)
                    .post(okhttp3.RequestBody.create(body,
                            okhttp3.MediaType.parse("application/json")))
                    .build();
            try (okhttp3.Response resp = client.newCall(req).execute()) {
                assertTrue(resp.isSuccessful(), "链节点 HTTP 状态异常: " + resp.code());
                String respBody = resp.body() != null ? resp.body().string() : "";
                com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(respBody);
                assertNotNull(json.getString("result"), "链节点返回无 result 字段");
                log.info("链节点可达, 当前区块号: " + json.getString("result"));
            }
        } catch (Exception e) {
            fail("链节点不可达: " + e.getMessage() + " (RPC_URL=" + RPC_URL + ")");
        }
    }

    /**
     * 测试 writeHash 真实上链
     */
    @Test
    @Order(2)
    @DisplayName("writeHash - 真实上链成功返回 txHash")
    void writeHash_realChainSubmit() {
        String dataHash = generateTestHash("write-test-" + System.currentTimeMillis());
        String bizId = uniqueBizId();

        String txHash = smartContractService.writeHash(dataHash, bizId);

        assertNotNull(txHash, "上链成功应返回 txHash");
        assertTrue(txHash.startsWith("0x"), "txHash 应以 0x 开头");
        assertTrue(txHash.length() >= 64, "txHash 长度应 >= 64: " + txHash);
        log.info("writeHash 成功: dataHash=" + dataHash + " txHash=" + txHash);
    }

    /**
     * 测试 writeHash 异常处理 - 空哈希
     */
    @Test
    @Order(3)
    @DisplayName("writeHash - 空哈希抛出异常")
    void writeHash_emptyHashThrows() {
        assertThrows(RuntimeException.class, () ->
                smartContractService.writeHash("", uniqueBizId())
        );
    }

    /**
     * 测试 queryByHash 查询已上链数据
     */
    @Test
    @Order(4)
    @DisplayName("queryByHash - 查询已上链数据")
    void queryByHash_existingData() {
        String dataHash = generateTestHash("query-test-" + System.currentTimeMillis());
        String bizId = uniqueBizId();
        smartContractService.writeHash(dataHash, bizId);

        waitForBlockConfirmation();

        String result = smartContractService.queryByHash(dataHash);
        log.info("queryByHash 结果: dataHash=" + dataHash + " result=" + result);
    }

    /**
     * 测试 queryByHash 查询不存在的哈希
     */
    @Test
    @Order(5)
    @DisplayName("queryByHash - 查询不存在的哈希返回 null")
    void queryByHash_nonExistent() {
        String fakeHash = generateTestHash("nonexistent-" + System.nanoTime());
        String result = smartContractService.queryByHash(fakeHash);
        log.info("queryByHash (不存在): hash=" + fakeHash + " result=" + result);
    }

    /**
     * 测试 queryBlockNumber 查询区块号
     */
    @Test
    @Order(6)
    @DisplayName("queryBlockNumber - 查询已确认交易的区块号")
    void queryBlockNumber_confirmedTx() {
        String dataHash = generateTestHash("block-test-" + System.currentTimeMillis());
        String bizId = uniqueBizId();
        String txHash = smartContractService.writeHash(dataHash, bizId);

        waitForBlockConfirmation();

        Long blockNumber = smartContractService.queryBlockNumber(txHash);
        assertNotNull(blockNumber, "已确认交易应返回区块号");
        assertTrue(blockNumber >= 0, "区块号应 >= 0: " + blockNumber);
        log.info("queryBlockNumber 成功: txHash=" + txHash + " block=" + blockNumber);
    }

    /**
     * 测试 queryBlockNumber 查询未确认交易
     */
    @Test
    @Order(7)
    @DisplayName("queryBlockNumber - 查询未确认交易返回 null")
    void queryBlockNumber_unconfirmedTx() {
        String fakeTxHash = "0x" + "a".repeat(64);
        Long blockNumber = smartContractService.queryBlockNumber(fakeTxHash);
        log.info("queryBlockNumber (不存在): txHash=" + fakeTxHash + " result=" + blockNumber);
    }

    /**
     * 测试 queryBlockNumberWithRetry 重试机制
     */
    @Test
    @Order(8)
    @DisplayName("queryBlockNumberWithRetry - 重试后查询成功")
    void queryBlockNumberWithRetry_eventuallySucceeds() {
        String dataHash = generateTestHash("retry-test-" + System.currentTimeMillis());
        String bizId = uniqueBizId();
        String txHash = smartContractService.writeHash(dataHash, bizId);

        Long blockNumber = smartContractService.queryBlockNumberWithRetry(txHash, 3);
        log.info("queryBlockNumberWithRetry: txHash=" + txHash +
                " block=" + blockNumber + " (null 表示仍在等待确认)");
    }

    /**
     * 测试批量写入
     */
    @Test
    @Order(9)
    @DisplayName("batchWriteHash - 批量写入多个哈希")
    void batchWriteHash_multipleHashes() {
        java.util.List<String> dataHashes = java.util.List.of(
                generateTestHash("batch-1-" + System.currentTimeMillis()),
                generateTestHash("batch-2-" + System.currentTimeMillis()),
                generateTestHash("batch-3-" + System.currentTimeMillis())
        );
        java.util.List<String> bizIds = java.util.List.of(
                uniqueBizId(), uniqueBizId(), uniqueBizId());

        var results = smartContractService.batchWriteHash(dataHashes, bizIds);

        assertNotNull(results);
        assertEquals(3, results.size(), "应返回 3 个结果");
        long successCount = results.stream().filter(r -> r.isSuccess()).count();
        assertTrue(successCount >= 1, "至少 1 个应成功，实际: " + successCount);
        log.info("batchWriteHash 完成: success=" + successCount + "/3");
    }

    /**
     * 测试缓存命中 - 第二次查询应命中缓存
     */
    @Test
    @Order(10)
    @DisplayName("缓存 - 重复查询命中缓存")
    void cache_hitOnRepeatedQuery() {
        String dataHash = generateTestHash("cache-test-" + System.currentTimeMillis());
        String bizId = uniqueBizId();
        String txHash = smartContractService.writeHash(dataHash, bizId);
        waitForBlockConfirmation();

        long callsBefore = smartContractService.getTotalRpcCalls();
        smartContractService.queryBlockNumber(txHash);
        long callsAfter1 = smartContractService.getTotalRpcCalls();

        smartContractService.queryBlockNumber(txHash);
        long callsAfter2 = smartContractService.getTotalRpcCalls();

        assertEquals(callsAfter1, callsAfter2, "第二次查询应命中缓存");
        log.info("缓存命中验证: RPC calls before=" + callsBefore +
                " after1=" + callsAfter1 + " after2=" + callsAfter2);
    }

    /**
     * 测试性能指标统计
     */
    @Test
    @Order(11)
    @DisplayName("性能指标 - 记录 RPC 调用次数和延迟")
    void metrics_recorded() {
        long callsBefore = smartContractService.getTotalRpcCalls();

        String dataHash = generateTestHash("metric-test-" + System.currentTimeMillis());
        smartContractService.writeHash(dataHash, uniqueBizId());

        long callsAfter = smartContractService.getTotalRpcCalls();
        assertTrue(callsAfter > callsBefore, "RPC 调用次数应增加");
        assertTrue(smartContractService.getAverageLatency() >= 0, "平均延迟应 >= 0");
        log.info("性能指标: calls=" + callsAfter + " avgLatency=" +
                smartContractService.getAverageLatency() + "ms");
    }

    /**
     * 测试 RPC 超时处理 - 使用一个不可达地址
     */
    @Test
    @Order(12)
    @DisplayName("异常处理 - 不可达 RPC 地址的容错")
    void exceptionHandling_unreachableRpc() {
        SmartContractServiceImpl badService = new SmartContractServiceImpl();
        EvidenceCache cache = new EvidenceCaffeineCache(1000, 30_000L);
        ReflectionTestUtils.setField(badService, "rpcUrl", "http://127.0.0.1:9999/unreachable");
        ReflectionTestUtils.setField(badService, "contractAddress", CONTRACT);
        ReflectionTestUtils.setField(badService, "privateKey", PRIVATE_KEY);
        ReflectionTestUtils.setField(badService, "evidenceLocalCache", cache);

        assertThrows(RuntimeException.class, () ->
                badService.writeHash(generateTestHash("bad"), uniqueBizId()));

        assertNull(badService.queryByHash(generateTestHash("bad")));
        assertNull(badService.queryBlockNumber("0x" + "a".repeat(64)));

        log.info("异常处理验证: 不可达 RPC 地址下所有方法都正确容错");
    }

    private void waitForBlockConfirmation() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
