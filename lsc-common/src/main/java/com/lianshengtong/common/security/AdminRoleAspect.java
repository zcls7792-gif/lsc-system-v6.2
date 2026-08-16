package com.lianshengtong.common.security;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Aspect
@Component
public class AdminRoleAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminRoleAspect.class);

    private static final String HEADER_ADMIN_ROLE = "X-Admin-Role";
    private static final String HEADER_ADMIN_ID = "X-User-Id";

    @Autowired
    private HttpServletRequest request;

    @Around("@annotation(requireAdminRole)")
    public Object checkRole(ProceedingJoinPoint pjp, RequireAdminRole requireAdminRole) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        int requiredRole = requireAdminRole.value();
        String methodName = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "无法获取请求上下文");
        }
        HttpServletRequest req = attrs.getRequest();

        String roleHeader = req.getHeader(HEADER_ADMIN_ROLE);
        if (roleHeader == null || roleHeader.isBlank()) {
            log.warn("[AdminRoleAspect] 缺少{}头，拒绝访问 method={}", HEADER_ADMIN_ROLE, methodName);
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "未授权：缺少管理员身份标识");
        }

        int userRole;
        try {
            userRole = Integer.parseInt(roleHeader.trim());
        } catch (NumberFormatException e) {
            log.warn("[AdminRoleAspect] 角色值格式错误 role={} method={}", roleHeader, methodName);
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "角色标识格式错误");
        }

        if (userRole < requiredRole) {
            String adminId = req.getHeader(HEADER_ADMIN_ID);
            log.warn("[AdminRoleAspect] 权限不足 adminId={} role={} required={} method={}",
                    adminId, userRole, requiredRole, methodName);
            throw new BizException(ResultCode.FORBIDDEN.getCode(),
                    "权限不足，需要角色等级 >= " + requiredRole);
        }

        return pjp.proceed();
    }


    public AdminRoleAspect() {}

    public AdminRoleAspect(HttpServletRequest request) {
        this.request = request;
    }

    public HttpServletRequest getRequest() { return request; }
    public void setRequest(HttpServletRequest request) { this.request = request; }
}
