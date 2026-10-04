package com.lianshengtong.supplychain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order")
public class PurchaseOrder {
    @TableId(type = IdType.INPUT)
    private Long poId;
    private String poNo;
    private Long supplierId;
    private Long buyerEntityId;
    private String currency;
    private Long totalCent;
    private String status;
    private Long approvedBy;
    private LocalDate paymentDueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
