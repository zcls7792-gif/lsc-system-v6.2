package com.lianshengtong.evidence.service;

/**
 * 智能合约交互服务接口
 * <p>
 * 仅支持写入(上链)和查询，禁止修改删除。
 * 区块链数据不可篡改特性决定了已上链数据无法修改或删除。
 * </p>
 */
public interface SmartContractService {

    /**
     * 写入存证哈希到智能合约(上链)
     *
     * @param dataHash 数据哈希(SHA-256)
     * @param bizId    业务ID(可选，作为索引)
     * @return 链上交易哈希
     */
    String writeHash(String dataHash, String bizId);

    /**
     * 查询链上存证
     *
     * @param dataHash 数据哈希
     * @return 链上交易哈希(未找到返回null)
     */
    String queryByHash(String dataHash);

    /**
     * 查询区块高度
     *
     * @param txHash 交易哈希
     * @return 区块高度
     */
    Long queryBlockNumber(String txHash);

    /**
     * 查询区块高度（带重试机制，指数退避）
     *
     * @param txHash      交易哈希
     * @param maxRetries  最大重试次数
     * @return 区块高度（全部重试失败返回null）
     */
    Long queryBlockNumberWithRetry(String txHash, int maxRetries);
}
