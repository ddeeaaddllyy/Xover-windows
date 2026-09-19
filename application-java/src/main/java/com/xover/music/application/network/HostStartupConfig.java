package com.xover.music.application.network;

import java.nio.file.Path;
import java.util.Objects;

public record HostStartupConfig(
    String bindHost,
    String advertisedHost,
    int port,
    Path trackFile
) {
    public HostStartupConfig {
        bindHost = normalizeHost(bindHost, "0.0.0.0");
        advertisedHost = normalizeHost(advertisedHost, "127.0.0.1");
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be in range 1..65535");
        }
        Objects.requireNonNull(trackFile, "trackFile");
    }

    public String mediaUri() {
        return "http://" + advertisedHost + ":" + port + "/track";
    }

    private static String normalizeHost(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
