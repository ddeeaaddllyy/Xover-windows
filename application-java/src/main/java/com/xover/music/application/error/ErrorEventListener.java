package com.xover.music.application.error;

@FunctionalInterface
public interface ErrorEventListener {
    void onError(ErrorEvent event);
}
