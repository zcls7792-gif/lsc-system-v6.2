package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.LscEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LscEventMapper extends BaseMapper<LscEvent> {

    @Select("SELECT * FROM lsc_event WHERE user_id = #{userId} AND business_key = #{bizKey}")
    LscEvent selectByUserAndBizKey(@Param("userId") Long userId, @Param("bizKey") String bizKey);
}
