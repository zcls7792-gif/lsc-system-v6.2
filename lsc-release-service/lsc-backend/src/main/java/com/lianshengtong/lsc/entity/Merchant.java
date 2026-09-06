package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
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
    private String level;
    private BigDecimal monthlyRevenue;
    private String province, city, district;
    private String addressDetail;
    private BigDecimal longitude, latitude;
    private String contactPhone;
    private LocalDateTime createdAt;
}
