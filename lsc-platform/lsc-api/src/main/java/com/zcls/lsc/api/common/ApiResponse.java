package com.zcls.lsc.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 返回体。
 *
 * @param <T> 数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final int code;
    private final String message;
    private final T data;
    private final String requestId;

    private ApiResponse(boolean success, int code, String message, T data, String requestId) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 0, "OK", data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(true, 0, "OK", data, requestId);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(false, code, message, null, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, String requestId) {
        return new ApiResponse<>(false, code, message, null, requestId);
    }

    public boolean isSuccess() { return success; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getRequestId() { return requestId; }
}
