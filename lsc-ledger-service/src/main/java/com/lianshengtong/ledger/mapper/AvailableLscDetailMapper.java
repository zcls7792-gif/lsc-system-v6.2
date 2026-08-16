package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 可用 LSC 明细 Mapper
 * <p>
 * 过期转回扫描 status=1 且 expire_date &lt; today 的记录。
 * </p>
 */
@Mapper
public interface AvailableLscDetailMapper extends BaseMapper<AvailableLscDetail> {

    /**
     * 扫描某用户已过期但未转回的可用LSC明细(status=1 且 expire_date < today)。
     *
     * @param userId 用户ID
     * @param today  今天
     * @param limit  批量大小
     * @return 待转回明细列表
     */
    List<AvailableLscDetail> selectExpiredForTransfer(@Param("userId") Long userId,
                                                     @Param("today") LocalDate today,
                                                     @Param("limit") int limit);

    /**
     * 批量扫描所有用户已过期但未转回的可用LSC明细。
     * <p>用于 expireTransferAll 全网过期转回，避免 N+1 查询。</p>
     *
     * @param today 今天
     * @param limit 单次批量大小
     * @return 待转回明细列表
     */
    List<AvailableLscDetail> selectBatchExpiredForTransfer(@Param("today") LocalDate today,
                                                           @Param("limit") int limit);

    /**
     * 批量更新明细状态为已过期转回。
     *
     * @param ids 明细ID列表
     * @param status 目标状态
     * @return 影响行数
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids,
                          @Param("status") Integer status);
}
