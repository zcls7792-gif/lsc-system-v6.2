package com.lianshengtong.evidence.config;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 补齐 EvidenceGlobalExceptionHandler 兜底分支覆盖率 (I-06)，见 LSC_V6.2_Reports/LSC_V6.2_Code_Quality_Completeness_Audit_20260822.md
 */
@DisplayName("EvidenceGlobalExceptionHandler 兜底分支补齐测试")
class EvidenceGlobalExceptionHandlerCoverageTest {

    private EvidenceGlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new EvidenceGlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("handleException: 普通 Exception 兜底返回 500")
    void handleException_genericException_returns500() {
        Exception ex = new Exception("系统内部错误");

        ResponseEntity<R<Void>> resp = handler.handleException(ex, request);

        assertEquals(500, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(500, resp.getBody().getCode());
        assertEquals("系统错误，请稍后重试", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("handleException: RuntimeException 子类也走兜底 500")
    void handleException_runtimeException_returns500() {
        Exception ex = new RuntimeException("未知运行时异常");

        ResponseEntity<R<Void>> resp = handler.handleException(ex, request);

        assertEquals(500, resp.getStatusCodeValue());
        assertEquals("系统错误，请稍后重试", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("handleIllegalArgument: 返回 400 且不泄露内部异常详情")
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("非法参数详情");

        ResponseEntity<R<Void>> resp = handler.handleIllegalArgument(ex, request);

        assertEquals(400, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertEquals("请求参数不合法", resp.getBody().getMessage());
        assertFalse(resp.getBody().getMessage().contains("非法参数详情"));
    }

    @Test
    @DisplayName("handleMissingParam: 返回 400 且包含缺失参数名")
    void handleMissingParam_returns400WithParamName() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("userId", "Long");

        ResponseEntity<R<Void>> resp = handler.handleMissingParam(ex, request);

        assertEquals(400, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("userId"));
        assertTrue(resp.getBody().getMessage().contains("缺少必要参数"));
    }

    @Test
    @DisplayName("handleBizException: 带 code 的 BizException 返回 400 且 code 匹配")
    void handleBizException_withCode_returns400AndMatchingCode() {
        BizException ex = new BizException(404, "记录不存在");

        ResponseEntity<R<Void>> resp = handler.handleBizException(ex, request);

        assertEquals(400, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(404, resp.getBody().getCode());
        assertEquals("记录不存在", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("handleBizException: 仅 message 的 BizException 默认 code=500")
    void handleBizException_defaultCode_returns400AndCode500() {
        BizException ex = new BizException("自定义业务错误");

        ResponseEntity<R<Void>> resp = handler.handleBizException(ex, request);

        assertEquals(400, resp.getStatusCodeValue());
        assertEquals(500, resp.getBody().getCode());
        assertEquals("自定义业务错误", resp.getBody().getMessage());
    }
}
