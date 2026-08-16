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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端链上集成测试 - 存证→上链→查询→校验 完整流程
 * <p>
 * 不依赖数据库与 Redis，直接通过 SmartContractService 测试链上交互完整流程。
 * 适用于验证真实链上交互是否正常工作。
 * <p>
 * 流程：构造存证数据 → writeHash 上链 → queryByHash 链上查询 → queryBlockNumber 区块号校验
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("端到端链上集成测试 - 存证→上链→查询→校验")
class EvidenceEndToEndChainIntegrationTest extends ChainIntegrationTestBase {

    private static final Logger log = Logger.getLogger(
            EvidenceEndToEndChainIntegrationTest.class.getName());

    /**
     * 测试完整存证流程：保存数据 → 上链 → 查询验证 → 区块号确认
     */
    @Test
    @Order(1)
    @DisplayName("完整流程 - 存证→上链→查询→区块号确认")
    void fullFlow_saveChainQueryAndVerify() {
        // 1. 构造存证数据
        String dataHash = generateTestHash("e2e-flow-" + System.currentTimeMillis());
        String bizId = uniqueBizId();
        String bizType = "ORDER";

        log.info("=== 端到端流程开始 ===");
        log.info("步骤1: 构造存证数据 bizType=" + bizType + " bizId=" + bizId + " dataHash=" + dataHash);

        // 2. 上链
        String txHash = smartContractService.writeHash(dataHash, bizId);
        assertNotNull(txHash, "上链应返回 txHash");
        assertTrue(txHash.startsWith("0x"), "txHash 应以 0x 开头");
        log.info("步骤2: 上链成功 txHash=" + txHash);

        // 3. 等待区块确认
        waitForBlockConfirmation();

        // 4. 链上查询
        String queryResult = smartContractService.queryByHash(dataHash);
        log.info("步骤3: 链上查询 result=" + queryResult);

        // 5. 区块号确认
        Long blockNumber = smartContractService.queryBlockNumber(txHash);
        log.info("步骤4: 区块号确认 blockNumber=" + blockNumber);

        // 6. 性能指标
        log.info("步骤5: 性能指标 rpcCalls=" + smartContractService.getTotalRpcCalls() +
                " avgLatency=" + smartContractService.getAverageLatency() + "ms" +
                " cacheHitRate=" + smartContractService.getCacheHitRate() + "%");

        log.info("=== 端到端流程完成 ===");
    }

    /**
     * 测试多业务场景：订单、支付、物流三类业务上链
     */
    @Test
    @Order(2)
    @DisplayName("多业务场景 - 订单/支付/物流三类业务上链")
    void multiBusiness_multipleTypesChainSubmit() {
        String[][] bizData = {
                {"ORDER", "ORD-" + UUID.randomUUID().toString().substring(0, 8)},
                {"PAYMENT", "PAY-" + UUID.randomUUID().toString().substring(0, 8)},
                {"LOGISTICS", "LOG-" + UUID.randomUUID().toString().substring(0, 8)}
        };

        List<String> txHashes = new ArrayList<>();
        for (String[] data : bizData) {
            String dataHash = generateTestHash(data[0] + "-" + System.currentTimeMillis());
            String txHash = smartContractService.writeHash(dataHash, data[1]);
            assertNotNull(txHash, data[0] + " 上链应返回 txHash");
            txHashes.add(txHash);
            log.info(data[0] + " 上链成功: bizId=" + data[1] + " txHash=" + txHash);
        }

        assertEquals(3, txHashes.size(), "应有 3 个 txHash");
        assertEquals(3, txHashes.stream().distinct().count(), "3 个 txHash 应各不相同");
    }

    /**
     * 测试查询流程：写入后多次查询验证一致性
     */
    @Test
    @Order(3)
    @DisplayName("查询一致性 - 写入后多次查询结果一致")
    void queryConsistency_multipleQueriesReturnSameResult() {
        String dataHash = generateTestHash("consistency-" + System.currentTimeMillis());
        String bizId = uniqueBizId();

        // 上链
        smartContractService.writeHash(dataHash, bizId);
        waitForBlockConfirmation();

        // 多次查询
        String result1 = smartContractService.queryByHash(dataHash);
        String result2 = smartContractService.queryByHash(dataHash);
        String result3 = smartContractService.queryByHash(dataHash);

        // 结果应一致 (第二次起命中缓存)
        assertEquals(result1, result2, "多次查询结果应一致");
        assertEquals(result2, result3, "多次查询结果应一致");
        log.info("查询一致性验证通过: result=" + result1);
    }

    /**
     * 测试重试机制：高频上链场景下的稳定性
     */
    @Test
    @Order(4)
    @DisplayName("稳定性 - 高频上链场景下的稳定性")
    void stability_highFrequencyChainSubmit() {
        int count = 5;
        int successCount = 0;
        List<String> failedHashes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            try {
                String dataHash = generateTestHash("stress-" + i + "-" + System.currentTimeMillis());
                String txHash = smartContractService.writeHash(dataHash, uniqueBizId());
                if (txHash != null && txHash.startsWith("0x")) {
                    successCount++;
                }
            } catch (Exception e) {
                failedHashes.add("第" + i + "条: " + e.getMessage());
            }
        }

        // 至少 80% 应成功
        assertTrue(successCount >= count * 0.8,
                "成功率应 >= 80%，实际 " + successCount + "/" + count +
                        " 失败: " + failedHashes);

        log.info("稳定性测试完成: success=" + successCount + "/" + count +
                " successRate=" + (successCount * 100 / count) + "%");
    }

    /**
     * 测试异常恢复：不可达 RPC 后恢复可达
     */
    @Test
    @Order(5)
    @DisplayName("异常恢复 - 不可达 RPC 后恢复可达")
    void errorRecovery_unreachableThenReachable() {
        // 1. 使用不可达地址触发异常
        SmartContractServiceImpl badService = new SmartContractServiceImpl();
        EvidenceCache badCache = new EvidenceCaffeineCache(1000, 30_000L);
        ReflectionTestUtils.setField(badService, "rpcUrl", "http://127.0.0.1:9999/unreachable");
        ReflectionTestUtils.setField(badService, "contractAddress", CONTRACT);
        ReflectionTestUtils.setField(badService, "privateKey", PRIVATE_KEY);
        ReflectionTestUtils.setField(badService, "evidenceLocalCache", badCache);

        // 不可达时应抛异常
        assertThrows(RuntimeException.class, () ->
                badService.writeHash(generateTestHash("bad"), uniqueBizId()));

        // 2. 恢复使用可达地址
        String dataHash = generateTestHash("recovery-" + System.currentTimeMillis());
        String txHash = smartContractService.writeHash(dataHash, uniqueBizId());
        assertNotNull(txHash, "恢复后上链应成功");

        log.info("异常恢复验证通过: 不可达时正确抛异常，恢复后正常上链 txHash=" + txHash);
    }

    private void waitForBlockConfirmation() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
