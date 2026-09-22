package com.xover.music.application.session.error;

import com.xover.music.application.common.error.XoverErrorCode;
import com.xover.music.application.common.error.XoverException;

public final class SessionStateException extends XoverException {
    public SessionStateException(String message) {
        super(
            XoverErrorCode.SESSION_STATE,
            "Session state error",
            message == null || message.isBlank() ? "Session state error" : message
        );
    }
}
