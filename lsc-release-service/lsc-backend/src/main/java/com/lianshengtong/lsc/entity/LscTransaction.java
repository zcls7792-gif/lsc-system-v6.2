package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("lsc_transaction")
public class LscTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer type;
    private Long amount;
    private Long beforeLocked, afterLocked;
    private Long beforeAvailable, afterAvailable;
    private Long counterpartyId;
    private String orderNo;
    private String idempotentKey;
    private LocalDateTime createdAt;
}
