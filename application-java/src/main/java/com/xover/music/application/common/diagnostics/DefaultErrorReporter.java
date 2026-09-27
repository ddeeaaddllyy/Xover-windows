package com.xover.music.application.common.diagnostics;

import com.xover.music.application.common.error.UnexpectedXoverException;
import com.xover.music.application.common.error.XoverException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public final class DefaultErrorReporter implements ErrorReporter, ErrorEventSource {
    private static final Pattern SENSITIVE_QUERY_PARAMETER = Pattern.compile(
        "(?i)([?&](?:access_token|auth_token|token|api_key|apikey|key|signature|sig|client_secret|oauth_token)=)[^&#\\s]+"
    );
    private static final Pattern OAUTH_VALUE = Pattern.compile("(?i)(OAuth\\s+)[A-Za-z0-9._~+/=-]+");
    private static final String REDACTED = "$1<redacted>";

    private final CopyOnWriteArrayList<ErrorEventListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void addListener(ErrorEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(ErrorEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void report(String operation, Throwable failure) {
        ErrorEvent event = toEvent(operation, failure);
        for (ErrorEventListener listener : listeners) {
            listener.onError(event);
        }
    }

    private ErrorEvent toEvent(String operation, Throwable failure) {
        Throwable safeFailure = failure == null ? new UnexpectedXoverException("Unknown error", null) : failure;
        XoverException xoverFailure = asXoverException(operation, safeFailure);
        Map<String, String> context = new LinkedHashMap<>();
        if (operation != null && !operation.isBlank()) {
            context.put("operation", redact(operation));
        }
        context.putAll(redactContext(xoverFailure.context()));

        return new ErrorEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            xoverFailure.code(),
            xoverFailure.title(),
            redact(xoverFailure.getMessage()),
            context,
            safeFailure.getClass().getName(),
            redact(stackTrace(safeFailure))
        );
    }

    private XoverException asXoverException(String operation, Throwable failure) {
        if (failure instanceof XoverException xoverFailure) {
            return xoverFailure;
        }
        return new UnexpectedXoverException(operation, failure);
    }

    private String stackTrace(Throwable failure) {
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private Map<String, String> redactContext(Map<String, String> context) {
        Map<String, String> redacted = new LinkedHashMap<>();
        context.forEach((key, value) -> redacted.put(key, redact(value)));
        return redacted;
    }

    private String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = SENSITIVE_QUERY_PARAMETER.matcher(value).replaceAll(REDACTED);
        return OAUTH_VALUE.matcher(redacted).replaceAll(REDACTED);
    }
}
