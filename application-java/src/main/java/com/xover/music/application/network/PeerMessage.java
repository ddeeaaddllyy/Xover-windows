package com.xover.music.application.network;

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
    long hostSentAtMillis
) {
    public static PeerMessage trackSelected(String trackName, String mediaUri) {
        return new PeerMessage(MessageType.TRACK_SELECTED, newNonce(), trackName, mediaUri, 0L, 0L, 0L, 0L, 0L);
    }

    public static PeerMessage playAt(long positionMillis, long startAtHostMillis) {
        return new PeerMessage(MessageType.PLAY_AT, newNonce(), "", "", positionMillis, startAtHostMillis, 0L, 0L, 0L);
    }

    public static PeerMessage pause(long positionMillis) {
        return new PeerMessage(MessageType.PAUSE, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L);
    }

    public static PeerMessage seek(long positionMillis) {
        return new PeerMessage(MessageType.SEEK, newNonce(), "", "", positionMillis, 0L, 0L, 0L, 0L);
    }

    public static PeerMessage timeSyncRequest(long clientSentAtMillis) {
        return new PeerMessage(MessageType.TIME_SYNC_REQUEST, newNonce(), "", "", 0L, 0L, clientSentAtMillis, 0L, 0L);
    }

    public static PeerMessage timeSyncResponse(String nonce, long clientSentAtMillis, long hostReceivedAtMillis, long hostSentAtMillis) {
        return new PeerMessage(MessageType.TIME_SYNC_RESPONSE, nonce, "", "", 0L, 0L, clientSentAtMillis, hostReceivedAtMillis, hostSentAtMillis);
    }

    private static String newNonce() {
        return UUID.randomUUID().toString();
    }
}
