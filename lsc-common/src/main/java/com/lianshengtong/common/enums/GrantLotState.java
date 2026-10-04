package com.lianshengtong.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GrantLot 状态（V7.7.2 第十二章 12.5）
 */
@Getter
@AllArgsConstructor
public enum GrantLotState {
    ACTIVE("ACTIVE", "释放中"),
    PAUSED("PAUSED", "暂停"),
    RELEASED("RELEASED", "释放完毕"),
    REVOKED("REVOKED", "已撤回");

    private final String code;
    private final String desc;
}
