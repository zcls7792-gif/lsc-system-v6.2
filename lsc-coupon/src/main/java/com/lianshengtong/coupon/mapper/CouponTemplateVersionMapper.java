package com.lianshengtong.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.coupon.entity.CouponTemplateVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CouponTemplateVersionMapper extends BaseMapper<CouponTemplateVersion> {

    @Select("SELECT * FROM coupon_template_version WHERE template_id = #{templateId} " +
            "AND effective_at <= NOW() ORDER BY template_version DESC LIMIT 1")
    CouponTemplateVersion selectLatestEffective(@Param("templateId") Long templateId);

    @Select("SELECT * FROM coupon_template_version WHERE source_type = 'REFERRAL' AND reward_tier = #{tier} " +
            "AND status = 'ACTIVE' AND effective_at <= NOW() ORDER BY template_version DESC LIMIT 1")
    CouponTemplateVersion selectReferralTemplateByTier(@Param("tier") int tier);
}
