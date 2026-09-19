package com.xover.music.domain;

import java.util.Objects;

/**
 * Immutable read model consumed by the UI. It deliberately contains only
 * display-safe values and no framework-specific objects.
 */
public record SessionViewState(
    DeviceRole role,
    ConnectionStatus connectionStatus,
    PlaybackStatus playbackStatus,
    String trackName,
    long positionMillis,
    long durationMillis,
    long clockOffsetMillis,
    String message
) {
    public static SessionViewState idle() {
        return new SessionViewState(
            DeviceRole.IDLE,
            ConnectionStatus.DISCONNECTED,
            PlaybackStatus.STOPPED,
            "",
            0L,
            0L,
            0L,
            "Ready"
        );
    }

    public SessionViewState {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(connectionStatus, "connectionStatus");
        Objects.requireNonNull(playbackStatus, "playbackStatus");
        trackName = trackName == null ? "" : trackName;
        message = message == null ? "" : message;
        positionMillis = Math.max(0L, positionMillis);
        durationMillis = Math.max(0L, durationMillis);
    }

    public SessionViewState withRole(DeviceRole nextRole) {
        return new SessionViewState(nextRole, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, message);
    }

    public SessionViewState withConnectionStatus(ConnectionStatus nextStatus) {
        return new SessionViewState(role, nextStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, message);
    }

    public SessionViewState withPlaybackStatus(PlaybackStatus nextStatus) {
        return new SessionViewState(role, connectionStatus, nextStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, message);
    }

    public SessionViewState withTrack(String nextTrackName, long nextDurationMillis) {
        return new SessionViewState(role, connectionStatus, playbackStatus, nextTrackName, positionMillis, nextDurationMillis, clockOffsetMillis, message);
    }

    public SessionViewState withPosition(long nextPositionMillis) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, nextPositionMillis, durationMillis, clockOffsetMillis, message);
    }

    public SessionViewState withClockOffset(long nextClockOffsetMillis) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, nextClockOffsetMillis, message);
    }

    public SessionViewState withMessage(String nextMessage) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, nextMessage);
    }
}
