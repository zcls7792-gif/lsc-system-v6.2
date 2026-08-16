package com.lianshengtong.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 商家入驻申请 DTO
 *
 * @author lsc
 */
@Data
public class MerchantApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商家用户ID (由Token解析注入) */
    private Long merchantId;

    /** 营业执照号 */
    @NotBlank(message = "营业执照号不能为空")
    private String businessLicense;

    /** 营业执照图片URL */
    @NotBlank(message = "营业执照图片不能为空")
    private String businessLicenseImg;

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

    /** 联系电话 */
    private String contactPhone;

    /** 营业时间 */
    private String businessHours;
}
