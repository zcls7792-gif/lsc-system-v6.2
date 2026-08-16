package com.lianshengtong.common.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class XssRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(XssRequestWrapper.class);

    private final Map<String, String[]> sanitizedParams;
    private final String sanitizedBody;

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
        this.sanitizedParams = sanitizeParameters(request);
        this.sanitizedBody = null;
    }

    private Map<String, String[]> sanitizeParameters(HttpServletRequest request) {
        Map<String, String[]> originalParams = request.getParameterMap();
        Map<String, String[]> sanitized = new HashMap<>();
        for (Map.Entry<String, String[]> entry : originalParams.entrySet()) {
            String key = LogSanitizer.sanitize(entry.getKey());
            String[] values = entry.getValue();
            String[] sanitizedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitizedValues[i] = LogSanitizer.sanitize(values[i]);
            }
            sanitized.put(key, sanitizedValues);
        }
        return sanitized;
    }

    @Override
    public String getParameter(String name) {
        String[] values = sanitizedParams.get(LogSanitizer.sanitize(name));
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(sanitizedParams);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(sanitizedParams.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return sanitizedParams.get(LogSanitizer.sanitize(name));
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return LogSanitizer.sanitize(value);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Enumeration<String> original = super.getHeaders(name);
        java.util.List<String> sanitized = new java.util.ArrayList<>();
        while (original.hasMoreElements()) {
            sanitized.add(LogSanitizer.sanitize(original.nextElement()));
        }
        return Collections.enumeration(sanitized);
    }
}
