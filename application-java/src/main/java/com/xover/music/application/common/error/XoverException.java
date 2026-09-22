package com.xover.music.application.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class XoverException extends RuntimeException {
    private final XoverErrorCode code;
    private final String title;
    private final Map<String, String> context;

    public XoverException(
        XoverErrorCode code,
        String title,
        String message
    ) {
        this(code, title, message, null, Map.of());
    }

    public XoverException(
        XoverErrorCode code,
        String title,
        String message,
        Throwable cause
    ) {
        this(code, title, message, cause, Map.of());
    }

    public XoverException(
        XoverErrorCode code,
        String title,
        String message,
        Throwable cause,
        Map<String, String> context
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.title = safeText(title, code.displayName());
        this.context = Map.copyOf(new LinkedHashMap<>(context == null ? Map.of() : context));
    }

    public XoverErrorCode code() {
        return code;
    }

    public String title() {
        return title;
    }

    public Map<String, String> context() {
        return context;
    }

    protected static Map<String, String> context(String key, Object value) {
        return Map.of(key, value == null ? "" : value.toString());
    }

    protected static Map<String, String> context(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, String> nextContext = new LinkedHashMap<>();
        nextContext.put(firstKey, firstValue == null ? "" : firstValue.toString());
        nextContext.put(secondKey, secondValue == null ? "" : secondValue.toString());
        return nextContext;
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
