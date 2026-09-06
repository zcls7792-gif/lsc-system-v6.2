package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LSC 账户流水
 */
@Data
@TableName("lsc_transaction")
public class LscTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 流水类型 */
    private Integer type;

    /** 变动金额 */
    private Long amount;

    /** 变动前冻结额 */
    private Long beforeLocked;

    /** 变动后冻结额 */
    private Long afterLocked;

    /** 变动前可用额 */
    private Long beforeAvailable;

    /** 变动后可用额 */
    private Long afterAvailable;

    /** 对手方用户ID */
    private Long counterpartyId;

    /** 订单号 */
    private String orderNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
