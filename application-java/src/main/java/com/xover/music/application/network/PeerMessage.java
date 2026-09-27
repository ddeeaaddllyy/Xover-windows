package com.xover.music.application.network;

import com.xover.music.domain.PlaylistTrack;

import java.util.List;
import java.util.UUID;

/**
 * Framework-neutral synchronization command exchanged through the transport.
 */
public record PeerMessage(
    MessageType type,
    String nonce,
    String trackName,
    String mediaUri,
    long positionMillis,
    long startAtHostMillis,
    long clientSentAtMillis,
    long hostReceivedAtMillis,
    long hostSentAtMillis,
    List<PlaylistTrack> playlist,
    int currentTrackIndex,
    String loadId
) {
    public PeerMessage {
        playlist = List.copyOf(playlist == null ? List.of() : playlist);
        loadId = loadId == null ? "" : loadId;
    }

    public static PeerMessage playlistUpdated(List<PlaylistTrack> playlist, int currentTrackIndex, String loadId) {
        return new PeerMessage(MessageType.PLAYLIST_UPDATED, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, playlist, currentTrackIndex, loadId);
    }

    public static PeerMessage trackSelected(String trackName, String mediaUri) {
        return new PeerMessage(MessageType.TRACK_SELECTED, newNonce(), trackName, mediaUri, 0L, 0L, 0L, 0L, 0L, List.of(), -1, newNonce());
    }

    public static PeerMessage playAt(String loadId, long positionMillis, long startAtHostMillis) {
        return new PeerMessage(MessageType.PLAY_AT, newNonce(), "", "", positionMillis, startAtHostMillis, 0L, 0L, 0L, List.of(), -1, loadId);
    }

    public static PeerMessage pause(String loadId, long positionMillis) {
        return new PeerMessage(MessageType.PAUSE, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L, List.of(), -1, loadId);
    }

    public static PeerMessage seek(String loadId, long positionMillis) {
        return new PeerMessage(MessageType.SEEK, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L, List.of(), -1, loadId);
    }

    public static PeerMessage trackReady(String loadId) {
        return new PeerMessage(MessageType.TRACK_READY, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, List.of(), -1, loadId);
    }

    public static PeerMessage trackFailed(String loadId) {
        return new PeerMessage(MessageType.TRACK_FAILED, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, List.of(), -1, loadId);
    }

    public static PeerMessage timeSyncRequest(long clientSentAtMillis) {
        return new PeerMessage(MessageType.TIME_SYNC_REQUEST, newNonce(), "", "", 0L, 0L, clientSentAtMillis, 0L, 0L, List.of(), -1, "");
    }

    public static PeerMessage timeSyncResponse(String nonce, long clientSentAtMillis, long hostReceivedAtMillis, long hostSentAtMillis) {
        return new PeerMessage(MessageType.TIME_SYNC_RESPONSE, nonce, "", "", 0L, 0L, clientSentAtMillis, hostReceivedAtMillis, hostSentAtMillis, List.of(), -1, "");
    }

    private static String newNonce() {
        return UUID.randomUUID().toString();
    }
}
