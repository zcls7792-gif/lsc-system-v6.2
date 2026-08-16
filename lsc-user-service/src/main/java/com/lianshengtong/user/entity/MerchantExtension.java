package com.lianshengtong.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商家扩展实体类 (对应 merchant_extensions 表)
 * 一对一关联 users.user_id (商家入驻后扩展信息)
 *
 * @author lsc
 */
@Data
@TableName("merchant_extensions")
public class MerchantExtension implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商家ID (与 users.user_id 一致, 雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long merchantId;

    /** 营业执照号 */
    private String businessLicense;

    /** 营业执照图片URL */
    private String businessLicenseImg;

    /** 信用评分 (默认100) */
    private Integer creditScore;

    /** AI风险评分 0-100 */
    private Integer aiRiskScore;

    /** 月营业额 */
    private BigDecimal monthlyRevenue;

    /** 核销限额档位 1-16, 0初始 */
    private Integer nhLimitLevel;

    /** 每日核销限额 (默认80) */
    private Integer dailyNhLimit;

    /** 监管账户号 */
    private String regulatoryAccountNo;

    /** 主账户号 */
    private String mainAccountNo;

    /** 最近核销日期 */
    private LocalDate lastNhDate;

    /** 处罚状态 0正常 1一级 2二级 3三级 4清退 */
    private Integer penaltyStatus;

    /** 门店名称 */
    private String storeName;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 区县 */
    private String district;

    /** 详细地址 */
    private String addressDetail;

    /** AI地址核验 0未核验 1通过 2可疑 3人工确认 */
    private Integer aiAddressVerified;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 联系电话 */
    private String contactPhone;

    /** 营业时间 */
    private String businessHours;

    /** 当日地址修改次数 */
    private Integer addressUpdateCount;

    /** 是否签署监管协议 0否 1是 */
    private Integer isSignedSupervision;

    /** 商家审核状态 0待审核 1通过 2拒绝 */
    private Integer auditStatus;

    /** 审核备注 (拒绝原因等) */
    @TableField(exist = false)
    private String auditRemark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
