package com.xover.music.domain;

import java.util.Objects;
import java.util.UUID;

public record PlaylistTrack(
    String id,
    String title,
    String sourceUrl
) {
    public PlaylistTrack {
        id = normalize(id);
        title = normalize(title);
        sourceUrl = normalize(sourceUrl);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Track id is required");
        }
        if (sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Track source URL is required");
        }
        if (title.isBlank()) {
            title = sourceUrl;
        }
    }

    public static PlaylistTrack create(String title, String sourceUrl) {
        return new PlaylistTrack(UUID.randomUUID().toString(), title, sourceUrl);
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }
}
