package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("b2b_order")
public class B2bOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long fromMerchantId;
    private Long toMerchantId;
    private String tradeDescription;
    private BigDecimal totalAmountRmb;
    private Long lscAmount;
    private String contractNo;
    private String tradeEvidenceUrls;
    private Integer aiVerificationResult; // 0=AI真实,1=AI可疑,2=人工真实,3=人工虚假
    private Integer status; // 0=待确认,1=已确认,2=已流转,3=已完成,4=已取消
    private String confirmedBy;
    private LocalDateTime createdAt;
}
