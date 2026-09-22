package com.xover.music.application.common.diagnostics;

public interface ErrorEventSource {
    void addListener(ErrorEventListener listener);

    void removeListener(ErrorEventListener listener);
}
