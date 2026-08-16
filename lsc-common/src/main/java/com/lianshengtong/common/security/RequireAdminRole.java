package com.lianshengtong.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员角色校验注解
 * <p>
 * 配合 {@link AdminRoleAspect} 使用，校验请求头中的 X-Admin-Role 是否满足最小角色要求。
 * 角色值定义: 1=普通管理员, 2=高级管理员, 3=超级管理员
 * Gateway JWT 过滤器在鉴权通过后将管理员角色透传为 X-Admin-Role 请求头。
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdminRole {

    /**
     * 所需最小角色等级 (1=普通 2=高级 3=超级)
     */
    int value() default 1;

    /**
     * 角色描述 (用于日志)
     */
    String description() default "";
}
