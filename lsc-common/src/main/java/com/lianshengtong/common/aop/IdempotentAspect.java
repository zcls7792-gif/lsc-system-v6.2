package com.lianshengtong.common.aop;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.Idempotent;
import com.lianshengtong.common.result.ResultCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 幂等切面 - 基于Redis实现接口级幂等
 * 配合@Idempotent注解使用
 * 幂等键 = 业务类型_用户ID_时间戳_4位随机数
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    private static final String IDEMPOTENT_PREFIX = "lsc:idempotent:";

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 解析SpEL获取幂等key值
        String keyExpr = idempotent.key();
        String keyValue = parseKey(keyExpr, method, joinPoint.getArgs());

        if (keyValue == null || keyValue.isEmpty()) {
            // 无key则不进行幂等校验
            return joinPoint.proceed();
        }

        String redisKey = IDEMPOTENT_PREFIX + method.getName() + ":" + keyValue;

        // 尝试获取锁(SETNX)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", idempotent.expireSeconds(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[Idempotent] 重复请求被拦截 key={} method={}", redisKey, method.getName());
            throw new BizException(ResultCode.IDEMPOTENT_DUPLICATE, idempotent.message());
        }

        log.debug("[Idempotent] 幂等key已设置 key={} expire={}s", redisKey, idempotent.expireSeconds());

        try {
            Object result = joinPoint.proceed();
            return result;
        } catch (RuntimeException e) {
            // 业务异常时释放幂等锁(允许重试)
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    private String parseKey(String keyExpr, Method method, Object[] args) {
        if (keyExpr == null || keyExpr.isEmpty()) return null;

        // 如果不以#开头，直接返回字面量
        if (!keyExpr.startsWith("#")) return keyExpr;

        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = discoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Expression expression = parser.parseExpression(keyExpr);
            Object value = expression.getValue(context);
            return value == null ? null : value.toString();
        } catch (RuntimeException e) {
            log.warn("[Idempotent] SpEL解析失败 expr={} error={}", keyExpr, e.getMessage());
            return keyExpr;
        }
    }


    public IdempotentAspect() {}


    public StringRedisTemplate getRedisTemplate() { return redisTemplate; }
    public void setRedisTemplate(StringRedisTemplate redisTemplate) { this.redisTemplate = redisTemplate; }
    public ExpressionParser getParser() { return parser; }
    public DefaultParameterNameDiscoverer getDiscoverer() { return discoverer; }
}
