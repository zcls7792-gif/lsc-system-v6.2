package com.lianshengtong.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coupon_template_version")
public class CouponTemplateVersion {
    @TableId(type = IdType.INPUT)
    private Long templateVersionId;
    private Long templateId;
    private Integer templateVersion;
    private String name;
    private Long faceCent;              // 面额(分)
    private Long minSpendCent;          // 门槛(分)
    private Integer validDays;          // 有效天数
    private String scopeType;           // ALL/CATEGORY/PRODUCT
    private String scopeDefinitionJson;
    private String sourceType;          // REFERRAL/ACTIVITY/MANUAL
    private Integer rewardTier;         // 推荐档位1-4
    private String status;
    private LocalDateTime effectiveAt;
    private LocalDateTime createdAt;
}
