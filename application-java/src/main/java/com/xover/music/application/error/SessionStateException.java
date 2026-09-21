package com.xover.music.application.error;

public final class SessionStateException extends XoverException {
    public SessionStateException(String message) {
        super(
            XoverErrorCode.SESSION_STATE,
            "Session state error",
            message == null || message.isBlank() ? "Session state error" : message
        );
    }
}
