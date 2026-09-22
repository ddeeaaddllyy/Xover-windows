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

public final class DefaultErrorReporter implements ErrorReporter, ErrorEventSource {
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
            context.put("operation", operation);
        }
        context.putAll(xoverFailure.context());

        return new ErrorEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            xoverFailure.code(),
            xoverFailure.title(),
            xoverFailure.getMessage(),
            context,
            safeFailure.getClass().getName(),
            stackTrace(safeFailure)
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
}
