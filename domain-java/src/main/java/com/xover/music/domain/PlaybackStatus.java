package com.xover.music.domain;

/**
 * Playback state independent from the concrete audio engine.
 */
public enum PlaybackStatus {
    STOPPED,
    LOADING,
    WAITING,
    READY,
    PLAYING,
    PAUSED,
    ERROR
}
