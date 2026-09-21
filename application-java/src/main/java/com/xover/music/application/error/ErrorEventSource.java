package com.xover.music.application.error;

public interface ErrorEventSource {
    void addListener(ErrorEventListener listener);

    void removeListener(ErrorEventListener listener);
}
