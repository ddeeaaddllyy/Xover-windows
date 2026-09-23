package com.xover.music.audio.resolver;

import com.xover.music.application.audio.error.AudioPlaybackException;
import com.xover.music.application.common.error.XoverException;

import java.io.IOException;
import java.net.URI;

public final class MediaResolver {
    private final SoundCloudMediaResolver soundCloud = new SoundCloudMediaResolver();
    private final YandexMusicMediaResolver yandexMusic = new YandexMusicMediaResolver();
    private final ExternalMediaResolver external = new ExternalMediaResolver();

    public URI resolve(URI mediaUri) {
        MediaSource source = MediaSourceDetector.detect(mediaUri);
        return switch (source) {
            case SOUNDCLOUD -> resolveSoundCloud(mediaUri);
            case YANDEX_MUSIC -> yandexMusic.resolve(mediaUri);
            case DIRECT_AUDIO -> mediaUri;
        };
    }

    private URI resolveSoundCloud(URI mediaUri) {
        try {
            return soundCloud.resolve(mediaUri);
        } catch (XoverException failure) {
            try {
                return external.resolveWithYtDlp(mediaUri).orElseThrow(() -> failure);
            } catch (IOException externalFailure) {
                externalFailure.addSuppressed(failure);
                throw AudioPlaybackException.resolutionFailed(mediaUri, externalFailure);
            }
        }
    }
}
