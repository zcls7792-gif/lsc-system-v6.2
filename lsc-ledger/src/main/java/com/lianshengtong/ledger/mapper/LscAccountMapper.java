package com.lianshengtong.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.ledger.entity.LscAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LscAccountMapper extends BaseMapper<LscAccount> {

    /** 行锁加载账户，用于事务内串行化 */
    @Select("SELECT * FROM lsc_account WHERE user_id = #{userId} FOR UPDATE")
    LscAccount selectByIdForUpdate(@Param("userId") Long userId);
}
