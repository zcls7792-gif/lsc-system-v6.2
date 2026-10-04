package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LSC 事件（事实源，不可修改）（V7.7.2 第六章 6.1）
 */
@Data
@TableName("lsc_event")
public class LscEvent {
    @TableId(type = IdType.INPUT)
    private Long eventId;
    private Long userId;
    private Long userEventSeq;
    private String eventType;
    private String businessKey;
    private String requestHash;
    private Long orderId;
    private Long refundId;
    private Long caseId;
    private LocalDate businessDate;
    private LocalDateTime occurredAt;
    private String ruleVersion;
    private Long originalEventId;
    private Integer payloadVersion;
    private String payloadJson;
    private String eventHash;
    private LocalDateTime createdAt;
}
