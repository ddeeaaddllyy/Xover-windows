package com.xover.music.app;

import com.xover.music.application.common.diagnostics.ErrorEventSource;
import com.xover.music.application.common.diagnostics.ErrorReporter;
import com.xover.music.application.session.ListeningSessionService;
import com.xover.music.application.library.LikedTracksService;
import com.xover.music.application.version.RemoteVersionPort;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = AppModule.class)
public interface AppComponent {
    ListeningSessionService listeningSessionService();
    LikedTracksService likedTracksService();
    RemoteVersionPort remoteVersionPort();

    ErrorEventSource errorEventSource();

    ErrorReporter errorReporter();
}
