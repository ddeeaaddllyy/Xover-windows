package com.xover.music.domain;

import java.util.Objects;

/** A local bookmark, independent of a session's playlist IDs. */
public record LikedTrack(String sourceUrl, String title) {
    public LikedTrack {
        sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl").trim();
        if (sourceUrl.isBlank()) throw new IllegalArgumentException("Track URL is required");
        title = Objects.toString(title, "").trim();
        if (title.isBlank()) title = sourceUrl;
    }
}
