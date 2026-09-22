package com.xover.music.application.common.diagnostics;

@FunctionalInterface
public interface ErrorEventListener {
    void onError(ErrorEvent event);
}
