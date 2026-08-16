package com.lianshengtong.common.idempotent;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 幂等键生成器
 * 规则：业务类型 + 用户ID + 时间戳(yyyyMMddHHmmssSSS) + 4位随机数
 */
public class IdempotentKeyGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String SEPARATOR = "_";

    public static String generate(String bizType, Long userId) {
        if (StrUtil.isBlank(bizType)) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String random = RandomUtil.randomNumbers(4);
        return bizType + SEPARATOR + userId + SEPARATOR + timestamp + SEPARATOR + random;
    }

    /**
     * 不带用户ID的幂等键（系统级任务场景）
     */
    public static String generateSystem(String bizType) {
        if (StrUtil.isBlank(bizType)) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String random = RandomUtil.randomNumbers(4);
        return "SYS_" + bizType + SEPARATOR + timestamp + SEPARATOR + random;
    }
}
