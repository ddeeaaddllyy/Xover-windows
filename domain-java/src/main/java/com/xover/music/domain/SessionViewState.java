package com.xover.music.domain;

import java.util.List;
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
    int localVolumePercent,
    List<PlaylistTrack> playlist,
    int currentTrackIndex,
    List<String> connectedPeerIds,
    List<String> controllerPeerIds,
    boolean canControlRoom,
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
            100,
            List.of(),
            -1,
            List.of(),
            List.of(),
            true,
            "Ready"
        );
    }

    public SessionViewState {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(connectionStatus, "connectionStatus");
        Objects.requireNonNull(playbackStatus, "playbackStatus");
        trackName = trackName == null ? "" : trackName;
        playlist = List.copyOf(playlist == null ? List.of() : playlist);
        currentTrackIndex = normalizeCurrentTrackIndex(playlist, currentTrackIndex);
        connectedPeerIds = List.copyOf(connectedPeerIds == null ? List.of() : connectedPeerIds);
        controllerPeerIds = List.copyOf(controllerPeerIds == null ? List.of() : controllerPeerIds);
        message = message == null ? "" : message;
        positionMillis = Math.max(0L, positionMillis);
        durationMillis = Math.max(0L, durationMillis);
        localVolumePercent = Math.max(0, Math.min(100, localVolumePercent));
    }

    public SessionViewState withRole(DeviceRole nextRole) {
        return new SessionViewState(nextRole, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withConnectionStatus(ConnectionStatus nextStatus) {
        return new SessionViewState(role, nextStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withPlaybackStatus(PlaybackStatus nextStatus) {
        return new SessionViewState(role, connectionStatus, nextStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withTrack(String nextTrackName, long nextDurationMillis) {
        return new SessionViewState(role, connectionStatus, playbackStatus, nextTrackName, positionMillis, nextDurationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withPosition(long nextPositionMillis) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, nextPositionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withClockOffset(long nextClockOffsetMillis) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, nextClockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withLocalVolumePercent(int nextLocalVolumePercent) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, nextLocalVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withPlaylist(List<PlaylistTrack> nextPlaylist, int nextCurrentTrackIndex) {
        List<PlaylistTrack> safePlaylist = List.copyOf(nextPlaylist == null ? List.of() : nextPlaylist);
        int safeIndex = normalizeCurrentTrackIndex(safePlaylist, nextCurrentTrackIndex);
        PlaylistTrack previousTrack = currentTrack();
        PlaylistTrack nextTrack = safeIndex < 0 ? null : safePlaylist.get(safeIndex);
        boolean sameTrack = previousTrack != null && nextTrack != null && previousTrack.id().equals(nextTrack.id());
        String nextTrackName = nextTrack == null ? "" : nextTrack.title();
        long nextPositionMillis = sameTrack ? positionMillis : 0L;
        long nextDurationMillis = sameTrack ? durationMillis : 0L;
        return new SessionViewState(role, connectionStatus, playbackStatus, nextTrackName, nextPositionMillis, nextDurationMillis, clockOffsetMillis, localVolumePercent, safePlaylist, safeIndex, connectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public PlaylistTrack currentTrack() {
        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            return null;
        }
        return playlist.get(currentTrackIndex);
    }

    public SessionViewState withMessage(String nextMessage) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, canControlRoom, nextMessage);
    }

    public SessionViewState withConnectedPeers(List<String> nextConnectedPeerIds) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, nextConnectedPeerIds, controllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withControllerPeers(List<String> nextControllerPeerIds) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, nextControllerPeerIds, canControlRoom, message);
    }

    public SessionViewState withCanControlRoom(boolean nextCanControlRoom) {
        return new SessionViewState(role, connectionStatus, playbackStatus, trackName, positionMillis, durationMillis, clockOffsetMillis, localVolumePercent, playlist, currentTrackIndex, connectedPeerIds, controllerPeerIds, nextCanControlRoom, message);
    }

    private static int normalizeCurrentTrackIndex(List<PlaylistTrack> playlist, int currentTrackIndex) {
        if (playlist == null || playlist.isEmpty()) {
            return -1;
        }
        return Math.max(0, Math.min(playlist.size() - 1, currentTrackIndex));
    }
}
