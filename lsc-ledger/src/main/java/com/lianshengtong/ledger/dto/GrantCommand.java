package com.lianshengtong.ledger.dto;

import lombok.Data;

/**
 * 赠送命令
 */
@Data
public class GrantCommand {
    private Long userId;
    private Long sourceItemId;        // 源订单行ID
    private Long grantUnit;           // 赠送 unit
    private String businessKey;       // 幂等键
    private String ruleVersion;
}
