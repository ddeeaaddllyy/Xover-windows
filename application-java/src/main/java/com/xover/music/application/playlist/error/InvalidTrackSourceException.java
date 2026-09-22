package com.xover.music.application.playlist.error;

import com.xover.music.application.common.error.XoverErrorCode;
import com.xover.music.application.common.error.XoverException;

import java.net.URI;

public final class InvalidTrackSourceException extends XoverException {
    private InvalidTrackSourceException(String message, Throwable cause, String sourceUrl) {
        super(
            XoverErrorCode.VALIDATION,
            "Invalid track URL",
            message,
            cause,
            context("sourceUrl", sourceUrl)
        );
    }

    public static InvalidTrackSourceException blank() {
        return new InvalidTrackSourceException("Track URL is required", null, "");
    }

    public static InvalidTrackSourceException malformed(String sourceUrl, Throwable cause) {
        return new InvalidTrackSourceException("Track URL is malformed", cause, sourceUrl);
    }

    public static InvalidTrackSourceException unsupportedScheme(URI sourceUri) {
        String scheme = sourceUri.getScheme() == null ? "" : sourceUri.getScheme();
        return new InvalidTrackSourceException(
            "Track URL must start with http:// or https://",
            null,
            sourceUri.toString() + " (scheme=" + scheme + ")"
        );
    }
}
