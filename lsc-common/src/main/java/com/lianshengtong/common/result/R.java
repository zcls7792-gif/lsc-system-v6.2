package com.lianshengtong.common.result;

import com.lianshengtong.common.observability.TraceIdHolder;

import java.io.Serializable;

public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    private R() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "success";
        r.traceId = TraceIdHolder.get();
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        r.traceId = TraceIdHolder.get();
        return r;
    }

    public static <T> R<T> ok(String message, T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = message;
        r.data = data;
        r.traceId = TraceIdHolder.get();
        return r;
    }

    public static <T> R<T> fail(String message) {
        R<T> r = new R<>();
        r.code = 500;
        r.message = message;
        r.traceId = TraceIdHolder.currentOrCreate();
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.traceId = TraceIdHolder.currentOrCreate();
        return r;
    }

    /** 供 GlobalExceptionHandler / 网关 filter 等场合显式指定 traceId（MDC 可能还未写入） */
    public static <T> R<T> fail(int code, String message, String traceId) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.traceId = traceId;
        return r;
    }

    public boolean isSuccess() {
        return this.code == 0;
    }

    public int getCode() { return code; }
    public void setCode(int v) { this.code = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public T getData() { return data; }
    public void setData(T v) { this.data = v; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String v) { this.traceId = v; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long v) { this.timestamp = v; }
}
