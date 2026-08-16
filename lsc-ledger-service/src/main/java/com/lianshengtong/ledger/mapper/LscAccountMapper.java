package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.LscAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LSC 账户 Mapper
 * <p>
 * 余额更新统一通过 MyBatis-Plus 的 updateById 触发乐观锁(version)，
 * {@link BaseMapper#updateById(Object)} 在 version 不匹配时返回 0。
 * </p>
 */
@Mapper
public interface LscAccountMapper extends BaseMapper<LscAccount> {

    /**
     * 初始化账户(若不存在则插入，存在则忽略)。
     * 用于发行/释放场景下保证账户行存在。
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int insertIfNotExists(@Param("userId") Long userId);

    /**
     * 查询所有锁定余额 > 0 的账户(跨分片，ShardingSphere 自动广播合并)
     * <p>用于每日释放任务加载待释放明细。</p>
     *
     * @return 锁定余额 > 0 的账户列表
     */
    List<LscAccount> selectAllLockedAccounts();

    /**
     * 查询所有存在过期可用明细的用户ID(跨分片)
     * <p>用于全网过期转回任务。</p>
     *
     * @return 有可用明细的用户ID列表(去重)
     */
    List<Long> selectUserIdsHavingAvailableDetails();
}
