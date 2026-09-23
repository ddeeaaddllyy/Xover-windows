package com.xover.music.audio.resolver;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

final class MediaSourceDetector {
    private MediaSourceDetector() {
    }

    static MediaSource detect(URI uri) {
        if (isSoundCloudPage(uri)) {
            return MediaSource.SOUNDCLOUD;
        }
        if (isYandexMusicTrack(uri)) {
            return MediaSource.YANDEX_MUSIC;
        }
        return MediaSource.DIRECT_AUDIO;
    }

    static boolean isSoundCloudPage(URI uri) {
        String host = normalizedHost(uri);
        return host.endsWith("soundcloud.com")
            && !host.startsWith("api.")
            && !host.startsWith("api-v2.");
    }

    static boolean isYandexMusicTrack(URI uri) {
        return yandexTrackId(uri).isPresent();
    }

    static Optional<String> yandexTrackId(URI uri) {
        String host = normalizedHost(uri);
        if (!host.equals("music.yandex.ru")
            && !host.equals("music.yandex.com")
            && !host.equals("m.music.yandex.ru")
            && !host.equals("m.music.yandex.com")) {
            return Optional.empty();
        }

        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("track".equalsIgnoreCase(parts[i])) {
                String candidate = parts[i + 1].replaceAll("[^0-9]", "");
                if (!candidate.isBlank()) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static String normalizedHost(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }
}
