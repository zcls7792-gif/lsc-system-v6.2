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
    private Integer status; // 0=处理中,1=失败,2=成功
    private LocalDate nhDate;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
