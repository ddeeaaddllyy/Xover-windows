package com.xover.music.audio.resolver;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MediaSourceDetectorTest {
    @Test
    void identifiesOnlyRealSoundCloudHosts() {
        assertEquals(MediaSource.SOUNDCLOUD, MediaSourceDetector.detect(URI.create("https://soundcloud.com/artist/track")));
        assertEquals(MediaSource.SOUNDCLOUD, MediaSourceDetector.detect(URI.create("https://www.soundcloud.com/artist/track")));
        assertEquals(MediaSource.DIRECT_AUDIO, MediaSourceDetector.detect(URI.create("https://fakesoundcloud.com/track.mp3")));
        assertEquals(MediaSource.DIRECT_AUDIO, MediaSourceDetector.detect(URI.create("https://api-v2.soundcloud.com/stream.mp3")));
    }

    @Test
    void acceptsOnlyNumericYandexTrackPathSegments() {
        assertEquals(Optional.of("12345"), MediaSourceDetector.yandexTrackId(URI.create("https://music.yandex.ru/album/9/track/12345")));
        assertEquals(Optional.empty(), MediaSourceDetector.yandexTrackId(URI.create("https://music.yandex.ru/album/9/track/12oops345")));
        assertEquals(Optional.empty(), MediaSourceDetector.yandexTrackId(URI.create("https://music.yandex.ru/album/9/track/")));
    }
}
