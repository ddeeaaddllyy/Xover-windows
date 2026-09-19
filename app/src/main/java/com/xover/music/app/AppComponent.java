package com.xover.music.app;

import com.xover.music.application.session.ListeningSessionService;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = AppModule.class)
public interface AppComponent {
    ListeningSessionService listeningSessionService();
}
