package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("nh_level")
public class NhLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String level;
    private BigDecimal minRevenue;
    private Long dailyLimit;
}
