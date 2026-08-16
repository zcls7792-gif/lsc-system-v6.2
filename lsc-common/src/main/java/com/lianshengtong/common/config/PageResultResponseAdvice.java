package com.lianshengtong.common.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.dto.PageResult;
import com.lianshengtong.common.result.R;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 分页结果统一转换 Advice
 * <p>
 * 将 MyBatis-Plus {@link IPage} 统一转换为 {@link PageResult}，
 * 使前后端分页字段命名一致(records/total/current/size/pages)，避免 IPage 直接序列化导致
 * 前端 .list / .records 取值不一致的运行期问题。
 * </p>
 * <p>
 * 仅当 classpath 存在 mybatis-plus 时生效(由 {@code @ConditionalOnClass} 控制)。
 * </p>
 *
 * @author lsc
 */
@RestControllerAdvice
@ConditionalOnClass(IPage.class)
public class PageResultResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // 对所有 R 返回类型生效，具体是否转换在 beforeBodyWrite 中按 data 类型判断
        return true;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class selectedConverterType, ServerHttpRequest request,
                                   ServerHttpResponse response) {
        if (body instanceof R<?> r) {
            Object data = r.getData();
            if (data instanceof IPage<?> page) {
                PageResult<?> pr = PageResult.of(
                        page.getRecords(),
                        page.getTotal(),
                        (int) page.getCurrent(),
                        (int) page.getSize());
                // 泛型擦除后可直接赋值
                ((R) r).setData(pr);
            }
        } else if (body instanceof IPage<?> page) {
            // 极少数直接返回 IPage 的场景
            return PageResult.of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
        }
        return body;
    }
}
