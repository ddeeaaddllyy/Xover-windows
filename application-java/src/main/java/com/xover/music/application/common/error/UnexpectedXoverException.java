package com.xover.music.application.common.error;

import java.util.Map;

public final class UnexpectedXoverException extends XoverException {
    public UnexpectedXoverException(String message, Throwable cause) {
        super(
            XoverErrorCode.UNEXPECTED,
            "Unexpected application error",
            message == null || message.isBlank() ? "Unexpected application error" : message,
            cause,
            Map.of()
        );
    }
}
