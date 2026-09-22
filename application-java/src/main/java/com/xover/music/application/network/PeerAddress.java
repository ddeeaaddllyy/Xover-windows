package com.xover.music.application.network;

import com.xover.music.application.network.error.InvalidNetworkAddressException;

public record PeerAddress(String host, int port) {
    public PeerAddress {
        if (host == null || host.isBlank()) {
            throw InvalidNetworkAddressException.blankHost();
        }
        host = host.trim();
        if (port <= 0 || port > 65_535) {
            throw InvalidNetworkAddressException.invalidPort(port);
        }
    }
}
