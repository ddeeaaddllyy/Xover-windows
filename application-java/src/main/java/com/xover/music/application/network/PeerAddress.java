package com.xover.music.application.network;

public record PeerAddress(String host, int port) {
    public PeerAddress {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host must not be blank");
        }
        host = host.trim();
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be in range 1..65535");
        }
    }
}
