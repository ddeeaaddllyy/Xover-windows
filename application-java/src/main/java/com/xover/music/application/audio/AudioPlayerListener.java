package com.xover.music.application.audio;

import java.time.Duration;

public interface AudioPlayerListener {
    default void onReady(Duration duration) {
    }

    default void onPositionChanged(Duration position) {
    }

    default void onError(String message, Throwable cause) {
    }
}
