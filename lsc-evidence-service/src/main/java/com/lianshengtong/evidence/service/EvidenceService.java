package com.lianshengtong.evidence.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.DailySnapshotRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 存证服务接口
 * <p>
 * 关键操作存证(每1小时或3000条，SHA-256哈希上链)、每日快照(Merkle树根)、
 * 3次重试失败存故障表30分钟扫描补传。
 * </p>
 */
public interface EvidenceService {

    /**
     * 保存存证
     * <p>计算 SHA-256 哈希，落库待上链；满 3000 条或每 1 小时批量上链。</p>
     *
     * @param bizType    业务类型
     * @param bizId      业务ID
     * @param dataHash   数据哈希(若空则对 payload 计算)
     * @param payload    原始数据(JSON)
     * @return 链上交易哈希(批量上链前返回存证记录ID字符串)
     */
    String saveEvidence(String bizType, String bizId, String dataHash, String payload);

    /**
     * 每日快照存证
     * <p>当日所有已上链存证记录的哈希构建 Merkle 树，根哈希上链。</p>
     *
     * @param date 快照日期(空则取昨天)
     * @return 快照记录
     */
    DailySnapshotRecord dailySnapshot(LocalDate date);

    /**
     * 查询存证记录
     *
     * @param bizType 业务类型(可空)
     * @param bizId   业务ID(可空)
     * @return 存证记录列表
     */
    List<BlockchainRecord> query(String bizType, String bizId);

    /**
     * 按日期校验存证
     * <p>比对当日所有存证记录哈希与链上数据是否一致。</p>
     *
     * @param date 日期
     * @return 是否校验通过
     */
    boolean verify(LocalDate date);

    /**
     * 存证记录分页查询(管理后台)
     *
     * @param page      页码
     * @param size      每页条数
     * @param batchNo   批次号/bizId(可空)
     * @param hash      数据哈希(可空)
     * @param txId      链上交易哈希(可空)
     * @param startDate 起始日期(可空)
     * @param endDate   截止日期(可空)
     * @return 分页结果
     */
    IPage<BlockchainRecord> listPage(Integer page, Integer size, String batchNo, String hash,
                                     String txId, String startDate, String endDate);

    /**
     * 根据ID查询存证记录详情
     *
     * @param id 存证记录ID
     * @return 存证记录
     */
    BlockchainRecord getById(Long id);

    /**
     * 按日期校验存证并返回报告(管理后台)
     *
     * @param date 日期
     * @return 校验报告(总数/通过数/失败数/是否通过)
     */
    Map<String, Object> verifyReport(LocalDate date);
}
