package com.lianshengtong.common.idempotent;

import java.lang.annotation.*;

/**
 * 幂等注解：配合Redis实现接口级幂等
 * 使用方式：@Idempotent(key = "#param.orderNo", expireSeconds = 300)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等key，支持SpEL
     */
    String key();

    /**
     * 过期时间（秒），默认5分钟
     */
    int expireSeconds() default 300;

    /**
     * 重复请求提示信息
     */
    String message() default "请勿重复提交";
}
