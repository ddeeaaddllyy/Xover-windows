package com.xover.music.application.network;

import com.xover.music.application.error.InvalidNetworkAddressException;

public record HostStartupConfig(
    String bindHost,
    String advertisedHost,
    int port
) {
    public HostStartupConfig {
        bindHost = normalizeHost(bindHost, "0.0.0.0");
        advertisedHost = normalizeHost(advertisedHost, "127.0.0.1");
        if (port <= 0 || port > 65_535) {
            throw InvalidNetworkAddressException.invalidPort(port);
        }
    }

    private static String normalizeHost(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
