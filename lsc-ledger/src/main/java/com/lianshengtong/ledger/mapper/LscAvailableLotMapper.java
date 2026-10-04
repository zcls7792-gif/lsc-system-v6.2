package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.LscAvailableLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LscAvailableLotMapper extends BaseMapper<LscAvailableLot> {

    /** FEFO 选择可用批次：未过期、available > 0，按 expire_at, available_at, id 排序 */
    @Select("SELECT * FROM lsc_available_lot WHERE user_id = #{userId} AND available_unit > 0 " +
            "AND expire_at > #{now} ORDER BY expire_at ASC, available_at ASC, available_lot_id ASC FOR UPDATE")
    List<LscAvailableLot> selectFefoAvailable(@Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);

    @Select("SELECT * FROM lsc_available_lot WHERE available_lot_id = #{id} FOR UPDATE")
    LscAvailableLot selectByIdForUpdate(@Param("id") Long id);
}
