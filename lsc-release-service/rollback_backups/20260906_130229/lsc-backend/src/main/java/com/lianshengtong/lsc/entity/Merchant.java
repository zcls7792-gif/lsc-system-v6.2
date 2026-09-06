package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String storeName;
    private String businessLicenseUrl;
    private String corporateAccountNo;
    private Integer regulatoryAgreementSigned;
    private Integer auditStatus; // 0=审核中,1=通过,2=驳回
    private Integer creditScore;
    private Integer aiRiskScore;
    private String level;
    private BigDecimal monthlyRevenue;
    private String nhLimitLevel; // 核销限额档位A-Z，0为初始额度
    private Integer dailyNhLimit;
    private String regulatoryAccountNo;
    private LocalDate lastNhDate;
    private String mainAccountNo;
    private Integer penaltyStatus; // 0=正常,1=一级处罚,2=二级处罚,3=三级处罚,4=四级处罚清退
    private String province, city, district;
    private String addressDetail;
    private Integer aiAddressVerified;
    private BigDecimal longitude, latitude;
    private String contactPhone;
    private String businessHours;
    private Integer addressUpdateCount;
    private LocalDateTime createdAt;
}
