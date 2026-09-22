package com.xover.music.application.common.diagnostics;

import com.xover.music.application.common.error.XoverErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ErrorEvent(
    String id,
    Instant occurredAt,
    XoverErrorCode code,
    String title,
    String message,
    Map<String, String> context,
    String exceptionClass,
    String trace
) {
    public ErrorEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(code, "code");
        title = title == null ? code.displayName() : title;
        message = message == null ? "" : message;
        context = Map.copyOf(context == null ? Map.of() : context);
        exceptionClass = exceptionClass == null ? "" : exceptionClass;
        trace = trace == null ? "" : trace;
    }

    public List<Map.Entry<String, String>> contextEntries() {
        return List.copyOf(context.entrySet());
    }
}
