package com.xover.music.application.audio;

import java.net.URI;
import java.time.Duration;

public interface AudioPlayerPort extends AutoCloseable {
    void setListener(AudioPlayerListener listener);

    void load(URI mediaUri);

    void play();

    void pause();

    void stop();

    void seek(Duration position);

    Duration currentPosition();

    Duration duration();

    @Override
    void close();
}
