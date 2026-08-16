package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.LscTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * LSC 流水 Mapper
 * <p>
 * 幂等校验依赖 idempotent_key 上的唯一索引 uk_idempotent_key。
 * 并发场景下第二个插入会因唯一索引冲突抛出 DuplicateKeyException，
 * 由业务层捕获并视为重复请求直接返回。
 * </p>
 */
@Mapper
public interface LscTransactionMapper extends BaseMapper<LscTransaction> {

    /**
     * 根据幂等键查询是否已存在流水(幂等校验前置)。
     *
     * @param idempotentKey 幂等键
     * @return 已存在的流水，无则返回 null
     */
    LscTransaction selectByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    /**
     * 按时间范围 + 流水类型聚合统计(用于对账)。
     * <p>聚合 SUM(amount) 与 COUNT(*)，跨分片由 ShardingSphere 自动汇总。</p>
     *
     * @param start 开始时间(含)
     * @param end   结束时间(不含)
     * @param types 流水类型集合(可空表示全部)
     * @return 聚合结果 Map: {totalAmount, totalCount}，无数据时返回空 List
     */
    List<Map<String, Object>> aggregateByTimeRange(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end,
                                                   @Param("types") List<Integer> types);
}
