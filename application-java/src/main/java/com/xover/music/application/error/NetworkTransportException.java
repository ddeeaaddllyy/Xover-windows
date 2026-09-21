package com.xover.music.application.error;

import com.xover.music.application.network.HostStartupConfig;
import com.xover.music.application.network.PeerAddress;

public final class NetworkTransportException extends XoverException {
    private NetworkTransportException(
        XoverErrorCode code,
        String title,
        String message,
        Throwable cause,
        String firstKey,
        Object firstValue,
        String secondKey,
        Object secondValue
    ) {
        super(code, title, message, cause, context(firstKey, firstValue, secondKey, secondValue));
    }

    public static NetworkTransportException hostStartupFailed(HostStartupConfig config, Throwable cause) {
        return new NetworkTransportException(
            XoverErrorCode.NETWORK_HOST_START,
            "Could not start host",
            "Could not start host on " + config.bindHost() + ":" + config.port(),
            cause,
            "bindHost",
            config.bindHost(),
            "port",
            config.port()
        );
    }

    public static NetworkTransportException connectionFailed(PeerAddress address, Throwable cause) {
        return new NetworkTransportException(
            XoverErrorCode.NETWORK_CONNECTION,
            "Could not connect to host",
            "Could not connect to " + address.host() + ":" + address.port(),
            cause,
            "host",
            address.host(),
            "port",
            address.port()
        );
    }

    public static NetworkTransportException sendFailed(String target, Throwable cause) {
        return new NetworkTransportException(
            XoverErrorCode.NETWORK_TRANSPORT,
            "Could not send sync message",
            "Could not send sync message to " + target,
            cause,
            "target",
            target,
            "operation",
            "send"
        );
    }
}
