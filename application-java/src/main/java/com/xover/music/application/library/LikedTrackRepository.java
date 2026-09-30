package com.xover.music.application.library;

import com.xover.music.domain.LikedTrack;
import java.util.List;

public interface LikedTrackRepository {
    List<LikedTrack> findAll();
    void save(LikedTrack track);
    void remove(String sourceUrl);
}
