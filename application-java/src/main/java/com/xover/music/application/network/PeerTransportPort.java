package com.xover.music.application.network;

public interface PeerTransportPort extends AutoCloseable {
    void setListener(PeerTransportListener listener);

    void startHost(HostStartupConfig config);

    void connect(PeerAddress address);

    void send(PeerMessage message);

    default void sendToPeer(String peerId, PeerMessage message) {
        send(message);
    }

    void broadcast(PeerMessage message);

    void disconnectPeer(String peerId);

    void disconnect();

    @Override
    void close();
}
