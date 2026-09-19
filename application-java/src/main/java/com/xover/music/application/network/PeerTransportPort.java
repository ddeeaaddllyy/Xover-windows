package com.xover.music.application.network;

public interface PeerTransportPort extends AutoCloseable {
    void setListener(PeerTransportListener listener);

    void startHost(HostStartupConfig config);

    void connect(PeerAddress address);

    void send(PeerMessage message);

    void broadcast(PeerMessage message);

    void disconnect();

    @Override
    void close();
}
