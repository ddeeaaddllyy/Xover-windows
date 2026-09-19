package com.xover.music.domain;

/**
 * Network lifecycle visible to the user.
 */
public enum ConnectionStatus {
    DISCONNECTED,
    HOSTING,
    CONNECTING,
    CONNECTED,
    ERROR
}
