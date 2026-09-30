package com.xover.music.application.library;

import com.xover.music.domain.LikedTrack;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LikedTracksServiceTest {
    private final List<LikedTrack> stored = new ArrayList<>();
    private final LikedTracksService service = new LikedTracksService(new LikedTrackRepository() {
        public List<LikedTrack> findAll() { return stored; }
        public void save(LikedTrack track) { stored.add(track); }
        public void remove(String sourceUrl) { stored.removeIf(track -> track.sourceUrl().equals(sourceUrl)); }
    });

    @Test
    void trimsSourceAndKeepsUrlWhenTitleIsBlank() {
        service.like(" https://example.com/song.mp3 ", " ");
        assertEquals(new LikedTrack("https://example.com/song.mp3", "https://example.com/song.mp3"), service.list().getFirst());
    }

    @Test
    void rejectsInvalidSourceBeforeWriting() {
        assertThrows(RuntimeException.class, () -> service.like("file:///song.mp3", "Song"));
        assertTrue(stored.isEmpty());
    }

    @Test
    void returnedListIsAnImmutableSnapshotAndUnlikeRemovesTheLink() {
        service.like("https://example.com/song", "Song");
        List<LikedTrack> snapshot = service.list();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        service.unlike(" https://example.com/song ");
        assertTrue(service.list().isEmpty());
        assertEquals(1, snapshot.size());
    }
}
