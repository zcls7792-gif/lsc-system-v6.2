package com.lianshengtong.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.coupon.entity.ReferralCounter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReferralCounterMapper extends BaseMapper<ReferralCounter> {

    @Select("SELECT * FROM referral_counter WHERE referrer_user_id = #{referrerId} FOR UPDATE")
    ReferralCounter selectByIdForUpdate(@Param("referrerId") Long referrerId);
}
