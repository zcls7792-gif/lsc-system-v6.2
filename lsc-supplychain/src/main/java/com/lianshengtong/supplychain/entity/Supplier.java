package com.lianshengtong.supplychain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {
    @TableId(type = IdType.INPUT)
    private Long supplierId;
    private String legalEntityInfo;
    private String licenseRef;
    private String contactEnc;
    private String bankAccountEnc;
    private Integer bankAccountVersion;
    private Integer paymentTermDays;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
