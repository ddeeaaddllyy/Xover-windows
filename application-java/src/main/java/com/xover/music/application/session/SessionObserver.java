package com.xover.music.application.session;

import com.xover.music.domain.SessionViewState;

public interface SessionObserver {
    void onStateChanged(SessionViewState state);
}
