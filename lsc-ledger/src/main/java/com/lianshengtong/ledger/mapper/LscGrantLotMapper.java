package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.LscGrantLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LscGrantLotMapper extends BaseMapper<LscGrantLot> {

    @Select("SELECT * FROM lsc_grant_lot WHERE grant_lot_id = #{id} FOR UPDATE")
    LscGrantLot selectByIdForUpdate(@Param("id") Long id);

    /** 查询需要释放的活跃批次：first_release_date <= 业务日 且 state=ACTIVE 且 refund_hold=0 */
    @Select("SELECT * FROM lsc_grant_lot WHERE user_id = #{userId} AND state = 'ACTIVE' " +
            "AND refund_hold = 0 AND first_release_date <= #{bizDate} " +
            "AND (last_processed_date IS NULL OR last_processed_date < #{bizDate}) " +
            "ORDER BY grant_lot_id FOR UPDATE")
    List<LscGrantLot> selectActiveLotsForRelease(@Param("userId") Long userId,
                                                  @Param("bizDate") LocalDate bizDate);
}
