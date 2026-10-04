package com.lianshengtong.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 申诉记录（V7.7.2 第十章 10.2）
 */
@Data
@TableName("appeal")
public class Appeal {
    private Long appealId;
    private Long caseId;
    private Long userId;
    private LocalDateTime submittedAt;
    private LocalDateTime replyDeadline;
    private String status;
    private Long reviewerId;
    private String decisionRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
