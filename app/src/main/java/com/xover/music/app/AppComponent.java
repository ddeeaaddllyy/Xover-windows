package com.xover.music.app;

import com.xover.music.application.common.diagnostics.ErrorEventSource;
import com.xover.music.application.common.diagnostics.ErrorReporter;
import com.xover.music.application.session.ListeningSessionService;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = AppModule.class)
public interface AppComponent {
    ListeningSessionService listeningSessionService();

    ErrorEventSource errorEventSource();

    ErrorReporter errorReporter();
}
