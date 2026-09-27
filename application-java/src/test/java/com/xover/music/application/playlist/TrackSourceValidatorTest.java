package com.xover.music.application.playlist;

import com.xover.music.application.playlist.error.InvalidTrackSourceException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrackSourceValidatorTest {
    @Test
    void acceptsPublicHttpAndHttpsSources() {
        assertEquals(
            URI.create("https://soundcloud.com/artist/track"),
            TrackSourceValidator.requireSafeSource(" https://soundcloud.com/artist/track ")
        );
        assertEquals(
            URI.create("http://example.com/audio.mp3"),
            TrackSourceValidator.requireSafeSource("http://example.com/audio.mp3")
        );
        assertEquals(
            URI.create("https://8.8.8.8/audio.mp3"),
            TrackSourceValidator.requireSafeSource("https://8.8.8.8/audio.mp3")
        );
        assertEquals(
            URI.create("https://[2001:4860:4860::8888]/audio.mp3"),
            TrackSourceValidator.requireSafeSource("https://[2001:4860:4860::8888]/audio.mp3")
        );
    }

    @Test
    void rejectsLocalPrivateAndReservedHosts() {
        assertBlocked("http://localhost/audio.mp3");
        assertBlocked("http://speaker.local/audio.mp3");
        assertBlocked("http://nas/audio.mp3");
        assertBlocked("http://127.0.0.1/audio.mp3");
        assertBlocked("http://10.0.0.1/audio.mp3");
        assertBlocked("http://172.16.0.1/audio.mp3");
        assertBlocked("http://192.168.1.10/audio.mp3");
        assertBlocked("http://169.254.1.10/audio.mp3");
        assertBlocked("http://100.64.0.1/audio.mp3");
        assertBlocked("http://203.0.113.10/audio.mp3");
        assertBlocked("http://0.0.0.0/audio.mp3");
        assertBlocked("http://[::1]/audio.mp3");
        assertBlocked("http://[fc00::1]/audio.mp3");
        assertBlocked("http://[fe80::1]/audio.mp3");
        assertBlocked("http://[2001:db8::1]/audio.mp3");
    }

    @Test
    void rejectsUnsupportedOrAmbiguousSources() {
        assertBlocked("ftp://example.com/audio.mp3");
        assertBlocked("https:///audio.mp3");
        assertBlocked("https://user:password@example.com/audio.mp3");
        assertBlocked("http://127.1/audio.mp3");
        assertBlocked("https://example.com/" + "a".repeat(4_100));
    }

    private void assertBlocked(String sourceUrl) {
        assertThrows(InvalidTrackSourceException.class, () -> TrackSourceValidator.requireSafeSource(sourceUrl));
    }
}
