package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家
 */
@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 店铺名称 */
    private String storeName;

    /** 营业执照URL */
    private String businessLicenseUrl;

    /** 对公账号 */
    private String corporateAccountNo;

    /** 监管协议是否签署：0否 1是 */
    private Integer regulatoryAgreementSigned;

    /** 审核状态 */
    private Integer auditStatus;

    /** 信用分 */
    private Integer creditScore;

    /** 商家等级 */
    private String level;

    /** 月营业额 */
    private BigDecimal monthlyRevenue;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址 */
    private String addressDetail;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 联系电话 */
    private String contactPhone;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
