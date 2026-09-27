package com.xover.music.application.audio;

import java.time.Duration;

public interface AudioPlayerListener {
    default void onReady(String loadId, Duration duration) {
    }

    default void onPositionChanged(String loadId, Duration position) {
    }

    default void onError(String loadId, String message, Throwable cause) {
    }

    default void onEnded(String loadId) {
    }
}
