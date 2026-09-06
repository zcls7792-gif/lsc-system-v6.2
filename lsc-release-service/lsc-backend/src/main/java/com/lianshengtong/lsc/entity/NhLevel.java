package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商家兑付等级
 */
@Data
@TableName("nh_level")
public class NhLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 等级 */
    private String level;

    /** 最低营业额 */
    private BigDecimal minRevenue;

    /** 每日限额 */
    private Long dailyLimit;
}
