package com.xover.music.application.network.error;

import com.xover.music.application.common.error.XoverErrorCode;
import com.xover.music.application.common.error.XoverException;

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
