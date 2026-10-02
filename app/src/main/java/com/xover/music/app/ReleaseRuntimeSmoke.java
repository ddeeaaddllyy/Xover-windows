package com.xover.music.app;

import com.xover.music.audio.resolver.HttpTrackTitleLookup;
import com.xover.music.audio.resolver.MediaResolver;

import java.net.URI;

/** Minimal offline checks run against the obfuscated release jars. */
public final class ReleaseRuntimeSmoke {
    private ReleaseRuntimeSmoke() {
    }

    public static void verify() throws Exception {
        URI directAudio = URI.create("https://example.com/audio.mp3");
        if (!directAudio.equals(new MediaResolver().resolve(directAudio))) {
            throw new IllegalStateException("Direct audio URL was changed by media resolution");
        }
        if (new HttpTrackTitleLookup().lookup(directAudio).isPresent()) {
            throw new IllegalStateException("Direct audio URL was parsed as a music page");
        }
    }
}
