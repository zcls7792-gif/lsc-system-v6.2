package com.lianshengtong.evidence.integration;

import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.config.EvidenceCaffeineCache;
import com.lianshengtong.evidence.service.impl.SmartContractServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/**
 * 链上交互集成测试基类
 * <p>
 * 通过环境变量注入测试链参数，未配置时自动跳过测试：
 * <ul>
 *   <li>{@code CHAIN_RPC_URL} - 测试链 RPC 地址 (必填)</li>
 *   <li>{@code CHAIN_CONTRACT} - 测试链合约地址 (必填)</li>
 *   <li>{@code CHAIN_PK} - 测试链账户私钥 (必填)</li>
 *   <li>{@code CHAIN_INTEGRATION_TEST} - 设为 true 显式开启 (可选，默认根据 RPC 地址是否可达判定)</li>
 * </ul>
 * <p>
 * 运行方式：
 * <pre>
 * mvn -o test -Dtest=ChainIntegrationTest \
 *     -DCHAIN_RPC_URL=http://10.0.0.1:8545 \
 *     -DCHAIN_CONTRACT=0x... \
 *     -DCHAIN_PK=0x...
 * </pre>
 * 或通过系统环境变量：
 * <pre>
 * export CHAIN_RPC_URL=http://10.0.0.1:8545
 * export CHAIN_CONTRACT=0x...
 * export CHAIN_PK=0x...
 * mvn -o test -Dtest=ChainIntegrationTest
 * </pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ChainIntegrationTestBase {

    protected static final String RPC_URL = System.getenv("CHAIN_RPC_URL");
    protected static final String CONTRACT = System.getenv("CHAIN_CONTRACT");
    protected static final String PRIVATE_KEY = System.getenv("CHAIN_PK");
    protected static final boolean FORCE_ENABLE =
            "true".equalsIgnoreCase(System.getenv("CHAIN_INTEGRATION_TEST"));

    protected SmartContractServiceImpl smartContractService;
    protected EvidenceCache evidenceLocalCache;

    /**
     * 生成唯一的业务ID，避免上链数据冲突
     */
    protected String uniqueBizId() {
        return "TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成符合 64 位 hex 格式的测试哈希
     */
    protected String generateTestHash(String seed) {
        // 简单生成 64 位 hex 哈希用于测试 (非真实SHA-256，仅作上链参数)
        String hex = Integer.toHexString(seed.hashCode());
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 64) {
            sb.append(hex);
        }
        return sb.substring(0, 64);
    }

    /**
     * 检查测试链是否可用
     */
    @BeforeAll
    void validateChainAvailability() {
        boolean enabled = FORCE_ENABLE || (RPC_URL != null && CONTRACT != null && PRIVATE_KEY != null);
        Assumptions.assumeTrue(enabled,
                "未配置 CHAIN_RPC_URL/CHAIN_CONTRACT/CHAIN_PK，跳过链上集成测试。" +
                "如需运行，请设置这三个环境变量。");
    }

    @BeforeEach
    void setUpSmartContractService() {
        evidenceLocalCache = new EvidenceCaffeineCache(10000, 30_000L);
        smartContractService = new SmartContractServiceImpl();
        ReflectionTestUtils.setField(smartContractService, "rpcUrl", RPC_URL);
        ReflectionTestUtils.setField(smartContractService, "contractAddress", CONTRACT);
        ReflectionTestUtils.setField(smartContractService, "privateKey", PRIVATE_KEY);
        ReflectionTestUtils.setField(smartContractService, "evidenceLocalCache", evidenceLocalCache);
    }
}
