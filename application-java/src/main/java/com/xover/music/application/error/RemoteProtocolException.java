package com.xover.music.application.error;

public final class RemoteProtocolException extends XoverException {
    public RemoteProtocolException(String message, Throwable cause) {
        super(
            XoverErrorCode.REMOTE_PROTOCOL,
            "Remote sync protocol error",
            message == null || message.isBlank() ? "Remote sync protocol error" : message,
            cause
        );
    }
}
