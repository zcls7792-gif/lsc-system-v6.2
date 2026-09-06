package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("nh_record")
public class NhRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long merchantId;
    private Long lscAmount;
    private BigDecimal cashAmount;
    private Long availableBefore;
    private Long availableAfter;
    private BigDecimal fundBefore;
    private BigDecimal fundAfter;
    private String idempotentKey;
    private Integer version;
    private Integer status; // 0=待处理,1=处理中,2=成功,3=失败
    private LocalDate nhDate;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
