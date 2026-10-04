package com.lianshengtong.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("refund_order")
public class RefundOrder {
    @TableId(type = IdType.INPUT)
    private Long refundId;
    private String refundNo;
    private Long orderId;
    private String reason;
    private String status;
    private String benefitStatus;
    private Long rmbCent;
    private Long shippingRefundCent;
    private String channelRefundNo;
    private Long approvedBy;
    private LocalDateTime succeededAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
