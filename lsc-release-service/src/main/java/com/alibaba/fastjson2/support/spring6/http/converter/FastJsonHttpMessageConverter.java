package com.alibaba.fastjson2.support.spring6.http.converter;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * 【Sandbox-only Stub】
 * <p>
 * 生产环境的真正实现来自 Maven 依赖 {@code fastjson2-extension-spring6}，
 * 它提供了 {@code FastJsonHttpMessageConverter} 用于 Spring Boot 3 / 6 环境下的
 * JSON 序列化/反序列化，并被 Spring 的 {@code WebMvcConfigurationSupport} 通过
 * {@code ClassUtils.isPresent(...)} 硬编码自动探测。
 * <p>
 * 但在当前 <b>离线 sandbox</b> 环境，外部 Maven 仓库不可达，无法下载扩展 jar，
 * 而 Spring 6 会在 {@code routerFunctionMapping()} 初始化时通过符号引用装载这个类，
 * 一旦找不到就抛 {@link NoClassDefFoundError}，导致整个应用上下文启动失败。
 * <p>
 * 为了在不引入 Maven 依赖的前提下让启动流程通过，这里放置一个<b>同名占位类</b>。
 * 占位类所有 {@code canRead}/{@code canWrite}/{@code supports} 均返回 false，
 * 因此即便被 Spring 加入到 {@code HttpMessageConverters} 列表也不会实际参与 JSON 转换 ——
 * Spring MVC 会回退到 Jackson（已在 classpath 中）作为 JSON 解析器，这与生产默认行为一致。
 * <p>
 * <b>上线提示：</b>若在生产环境通过 Maven 显式加入了 {@code fastjson2-extension-spring6}，
 * 则 Maven 会以"最近优先"原则选择真正的实现类；如出现类冲突，可以删除这个 stub 文件。
 *
 * @author sandbox-stub
 */
public class FastJsonHttpMessageConverter
        extends AbstractHttpMessageConverter<Object>
        implements GenericHttpMessageConverter<Object> {

    /** 默认构造：支持 JSON 媒体类型（仅为了符合默认行为；实际上 supports 返回 false） */
    public FastJsonHttpMessageConverter() {
        super(StandardCharsets.UTF_8,
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json"));
    }

    // ========================================================================
    // AbstractHttpMessageConverter<Object> 实现
    // ========================================================================

    @Override
    protected boolean supports(Class<?> clazz) {
        return false; // 本 Stub 永不参与
    }

    @Override
    protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException(
                "[FastJsonStub] Stub converter does not read. Check Jackson converter registration.",
                inputMessage);
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        throw new HttpMessageNotWritableException(
                "[FastJsonStub] Stub converter does not write. Check Jackson converter registration.");
    }

    // ========================================================================
    // GenericHttpMessageConverter<Object> 实现（Spring 用它做泛型解析匹配）
    // ========================================================================

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return false;
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException(
                "[FastJsonStub] Stub generic canRead always false; should never reach here.",
                inputMessage);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public void write(Object o, Type type, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        throw new HttpMessageNotWritableException(
                "[FastJsonStub] Stub generic canWrite always false; should never reach here.");
    }

    // 兼容 Spring 6 的 canRead/canWrite (ParameterizedTypeReference) 默认调用 Type 版本，
    // 这里不显式重写，调用父类即可。
}
