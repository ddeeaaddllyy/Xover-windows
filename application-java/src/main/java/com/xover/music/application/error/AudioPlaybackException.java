package com.xover.music.application.error;

import java.net.URI;
import java.util.Map;

public final class AudioPlaybackException extends XoverException {
    private AudioPlaybackException(
        XoverErrorCode code,
        String title,
        String message,
        Throwable cause,
        Map<String, String> context
    ) {
        super(code, title, message, cause, context);
    }

    public static AudioPlaybackException resolutionFailed(URI sourceUri, Throwable cause) {
        return new AudioPlaybackException(
            XoverErrorCode.AUDIO_RESOLUTION,
            "Could not resolve media",
            "Could not resolve media: " + sourceUri,
            cause,
            context("sourceUri", sourceUri)
        );
    }

    public static AudioPlaybackException loadFailed(URI sourceUri, Throwable cause) {
        return new AudioPlaybackException(
            XoverErrorCode.AUDIO_PLAYBACK,
            "Could not load media",
            "Could not load media: " + sourceUri,
            cause,
            context("sourceUri", sourceUri)
        );
    }

    public static AudioPlaybackException engineFailed(Throwable cause) {
        return new AudioPlaybackException(
            XoverErrorCode.AUDIO_PLAYBACK,
            "Audio engine error",
            "Audio engine error",
            cause,
            Map.of()
        );
    }

    public static AudioPlaybackException fxThreadFailed(String message, Throwable cause) {
        return new AudioPlaybackException(
            XoverErrorCode.AUDIO_PLAYBACK,
            "JavaFX audio thread failed",
            message,
            cause,
            Map.of()
        );
    }
}
