package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 兑付（NH）记录
 */
@Data
@TableName("nh_record")
public class NhRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 商家ID */
    private Long merchantId;

    /** LSC 金额 */
    private Long lscAmount;

    /** 现金金额 */
    private BigDecimal cashAmount;

    /** 状态 */
    private Integer status;

    /** 兑付日期 */
    private LocalDate nhDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
