package com.xover.music.application.library;

import com.xover.music.application.playlist.TrackSourceValidator;
import com.xover.music.domain.LikedTrack;
import java.util.List;
import java.util.Objects;

public final class LikedTracksService {
    private final LikedTrackRepository repository;

    public LikedTracksService(LikedTrackRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<LikedTrack> list() {
        return List.copyOf(repository.findAll());
    }

    public void like(String sourceUrl, String title) {
        String validated = TrackSourceValidator.requireSafeSource(sourceUrl).toString();
        repository.save(new LikedTrack(validated, title));
    }

    public void unlike(String sourceUrl) {
        repository.remove(Objects.requireNonNull(sourceUrl).trim());
    }
}
