package com.lianshengtong.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_unit_allocation")
public class OrderUnitAllocation {
    @TableId(type = IdType.INPUT)
    private Long allocationId;
    private Long itemId;
    private Integer unitIndex;
    private Long saleCent;
    private Long couponCent;
    private Long lscUnit;
    private Long rmbCent;
    private Long grantUnit;
    private Long refundedRmbCent;
    private Long refundedLscUnit;
    private Long clawbackTargetUnit;
    private String returnStatus;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
}
