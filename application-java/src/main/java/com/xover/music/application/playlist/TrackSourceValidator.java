package com.xover.music.application.playlist;

import com.xover.music.application.playlist.error.InvalidTrackSourceException;

import java.net.*;
import java.util.Locale;

public final class TrackSourceValidator {
    private static final int MAX_SOURCE_URL_LENGTH = 4_096;

    private TrackSourceValidator() {
    }

    public static URI requireSafeSource(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw InvalidTrackSourceException.blank();
        }
        String trimmed = sourceUrl.trim();
        if (trimmed.length() > MAX_SOURCE_URL_LENGTH) {
            throw InvalidTrackSourceException.unsafe(trimmed, "URL is too long");
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw InvalidTrackSourceException.malformed(sourceUrl, ex);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw InvalidTrackSourceException.unsupportedScheme(uri);
        }
        if (uri.getUserInfo() != null) {
            throw InvalidTrackSourceException.unsafe(trimmed, "credentials in URL are not allowed");
        }

        String host = normalizedHost(uri, trimmed);
        if (isBlockedHostName(host)) {
            throw InvalidTrackSourceException.unsafe(trimmed, "local hostnames are not allowed");
        }
        rejectUnsafeIpLiteral(host, trimmed);
        return uri;
    }

    private static String normalizedHost(URI uri, String sourceUrl) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "host is required");
        }

        String normalized = stripIpv6Brackets(host.trim().toLowerCase(Locale.ROOT));
        if (normalized.contains(":")) {
            return normalized;
        }
        try {
            return IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "host is invalid");
        }
    }

    private static boolean isBlockedHostName(String host) {
        return "localhost".equals(host)
            || host.endsWith(".localhost")
            || host.endsWith(".local")
            || (!host.contains(".") && !host.contains(":"));
    }

    private static void rejectUnsafeIpLiteral(String host, String sourceUrl) {
        if (host.contains(":")) {
            rejectUnsafeIpv6Literal(host, sourceUrl);
            return;
        }
        if (host.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')) {
            rejectUnsafeIpv4Literal(host, sourceUrl);
        }
    }

    private static void rejectUnsafeIpv4Literal(String host, String sourceUrl) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "ambiguous IP address formats are not allowed");
        }

        int first = parseIpv4Part(parts[0], sourceUrl);
        int second = parseIpv4Part(parts[1], sourceUrl);
        int third = parseIpv4Part(parts[2], sourceUrl);

        boolean blocked = first == 0
            || first == 10
            || first == 127
            || first == 169 && second == 254
            || first == 172 && second >= 16 && second <= 31
            || first == 192 && second == 168
            || first == 100 && second >= 64 && second <= 127
            || first == 192 && second == 0 && (third == 0 || third == 2)
            || first == 198 && (second == 18 || second == 19)
            || first == 198 && second == 51 && third == 100
            || first == 203 && second == 0 && third == 113
            || first >= 224;
        if (blocked) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "private or reserved IP addresses are not allowed");
        }
    }

    private static int parseIpv4Part(String part, String sourceUrl) {
        if (part.isBlank() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "IP address is invalid");
        }
        int value = Integer.parseInt(part);
        if (value > 255) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "IP address is invalid");
        }
        return value;
    }

    private static void rejectUnsafeIpv6Literal(String host, String sourceUrl) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!(address instanceof Inet6Address inet6Address)) {
                rejectUnsafeIpv4Literal(address.getHostAddress(), sourceUrl);
                return;
            }

            byte[] bytes = inet6Address.getAddress();
            if (isIpv4MappedAddress(bytes)) {
                rejectUnsafeIpv4Literal(
                    (bytes[12] & 0xff) + "." + (bytes[13] & 0xff) + "." + (bytes[14] & 0xff) + "." + (bytes[15] & 0xff),
                    sourceUrl
                );
                return;
            }

            int first = bytes[0] & 0xff;
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20 && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8;
            if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || uniqueLocal
                || documentation) {
                throw InvalidTrackSourceException.unsafe(sourceUrl, "private or reserved IP addresses are not allowed");
            }
        } catch (IllegalArgumentException | UnknownHostException ex) {
            throw InvalidTrackSourceException.unsafe(sourceUrl, "IP address is invalid");
        }
    }

    private static boolean isIpv4MappedAddress(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
}
