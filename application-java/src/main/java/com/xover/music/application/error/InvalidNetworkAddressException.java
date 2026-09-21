package com.xover.music.application.error;

public final class InvalidNetworkAddressException extends XoverException {
    private InvalidNetworkAddressException(String message, String key, Object value) {
        super(
            XoverErrorCode.VALIDATION,
            "Invalid network address",
            message,
            null,
            context(key, value)
        );
    }

    public static InvalidNetworkAddressException blankHost() {
        return new InvalidNetworkAddressException("Host must not be blank", "host", "");
    }

    public static InvalidNetworkAddressException invalidPort(int port) {
        return new InvalidNetworkAddressException("Port must be in range 1..65535", "port", port);
    }
}
