package com.xover.music.application.common.error;

public enum XoverErrorCode {
    VALIDATION("Validation"),
    SESSION_STATE("Session state"),
    AUDIO_RESOLUTION("Media resolution"),
    AUDIO_PLAYBACK("Audio playback"),
    NETWORK_HOST_START("Host startup"),
    NETWORK_CONNECTION("Network connection"),
    NETWORK_TRANSPORT("Network transport"),
    REMOTE_PROTOCOL("Remote protocol"),
    UNEXPECTED("Unexpected failure");

    private final String displayName;

    XoverErrorCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
