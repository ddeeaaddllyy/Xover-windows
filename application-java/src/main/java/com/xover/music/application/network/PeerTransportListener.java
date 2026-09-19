package com.xover.music.application.network;

public interface PeerTransportListener {
    default void onTransportReady(String detail) {
    }

    default void onPeerConnected(String peerId) {
    }

    default void onPeerDisconnected(String peerId) {
    }

    default void onMessage(PeerMessage message) {
    }

    default void onTransportError(String message, Throwable cause) {
    }
}
