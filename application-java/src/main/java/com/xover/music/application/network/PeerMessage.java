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
    String loadId,
    boolean canControlRoom
) {
    public PeerMessage {
        playlist = List.copyOf(playlist == null ? List.of() : playlist);
        loadId = loadId == null ? "" : loadId;
    }

    public static PeerMessage playlistUpdated(List<PlaylistTrack> playlist, int currentTrackIndex, String loadId) {
        return new PeerMessage(MessageType.PLAYLIST_UPDATED, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, playlist, currentTrackIndex, loadId, false);
    }

    public static PeerMessage trackSelected(String trackName, String mediaUri) {
        return new PeerMessage(MessageType.TRACK_SELECTED, newNonce(), trackName, mediaUri, 0L, 0L, 0L, 0L, 0L, List.of(), -1, newNonce(), false);
    }

    public static PeerMessage playAt(String loadId, long positionMillis, long startAtHostMillis) {
        return new PeerMessage(MessageType.PLAY_AT, newNonce(), "", "", positionMillis, startAtHostMillis, 0L, 0L, 0L, List.of(), -1, loadId, false);
    }

    public static PeerMessage pause(String loadId, long positionMillis) {
        return new PeerMessage(MessageType.PAUSE, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L, List.of(), -1, loadId, false);
    }

    public static PeerMessage seek(String loadId, long positionMillis) {
        return new PeerMessage(MessageType.SEEK, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L, List.of(), -1, loadId, false);
    }

    public static PeerMessage trackReady(String loadId) {
        return new PeerMessage(MessageType.TRACK_READY, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, List.of(), -1, loadId, false);
    }

    public static PeerMessage trackFailed(String loadId) {
        return new PeerMessage(MessageType.TRACK_FAILED, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, List.of(), -1, loadId, false);
    }

    public static PeerMessage timeSyncRequest(long clientSentAtMillis) {
        return new PeerMessage(MessageType.TIME_SYNC_REQUEST, newNonce(), "", "", 0L, 0L, clientSentAtMillis, 0L, 0L, List.of(), -1, "", false);
    }

    public static PeerMessage timeSyncResponse(String nonce, long clientSentAtMillis, long hostReceivedAtMillis, long hostSentAtMillis) {
        return new PeerMessage(MessageType.TIME_SYNC_RESPONSE, nonce, "", "", 0L, 0L, clientSentAtMillis, hostReceivedAtMillis, hostSentAtMillis, List.of(), -1, "", false);
    }

    public static PeerMessage roomControlPermission(boolean allowed) {
        return new PeerMessage(MessageType.ROOM_CONTROL_PERMISSION, newNonce(), "", "", 0L, 0L, 0L, 0L, 0L, List.of(), -1, "", allowed);
    }

    public static PeerMessage playlistEditRequest(MessageType type, String trackId, String value, int toIndex) {
        if (type != MessageType.PLAYLIST_ADD_REQUEST && type != MessageType.PLAYLIST_REMOVE_REQUEST
            && type != MessageType.PLAYLIST_MOVE_REQUEST && type != MessageType.PLAYLIST_RENAME_REQUEST
            && type != MessageType.PLAYLIST_SELECT_REQUEST) {
            throw new IllegalArgumentException("Not a playlist edit request: " + type);
        }
        return new PeerMessage(type, newNonce(), value == null ? "" : value, trackId == null ? "" : trackId,
            0L, 0L, 0L, 0L, 0L, List.of(), toIndex, "", false);
    }

    public static PeerMessage playbackRequest(MessageType type, long positionMillis) {
        if (type != MessageType.PLAY_REQUEST && type != MessageType.PAUSE_REQUEST
            && type != MessageType.SEEK_REQUEST && type != MessageType.NEXT_TRACK_REQUEST
            && type != MessageType.BACK_TRACK_REQUEST) {
            throw new IllegalArgumentException("Not a playback request: " + type);
        }
        return new PeerMessage(type, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L,
            List.of(), -1, "", false);
    }

    private static String newNonce() {
        return UUID.randomUUID().toString();
    }
}
