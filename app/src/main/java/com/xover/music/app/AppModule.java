package com.xover.music.app;

import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.clock.Clock;
import com.xover.music.application.clock.SystemClock;
import com.xover.music.application.common.diagnostics.DefaultErrorReporter;
import com.xover.music.application.common.diagnostics.ErrorEventSource;
import com.xover.music.application.common.diagnostics.ErrorReporter;
import com.xover.music.application.network.PeerTransportPort;
import com.xover.music.application.session.ListeningSessionService;
import com.xover.music.application.sync.ClockSynchronizer;
import com.xover.music.audio.JavaFxAudioPlayer;
import com.xover.music.infrastructure.KtorPeerTransport;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Module
public final class AppModule {
    @Provides
    @Singleton
    Clock provideClock() {
        return new SystemClock();
    }

    @Provides
    @Singleton
    ScheduledExecutorService provideScheduler() {
        return Executors.newScheduledThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "xover-session");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Provides
    @Singleton
    ClockSynchronizer provideClockSynchronizer() {
        return new ClockSynchronizer();
    }

    @Provides
    @Singleton
    DefaultErrorReporter provideDefaultErrorReporter() {
        return new DefaultErrorReporter();
    }

    @Provides
    @Singleton
    ErrorReporter provideErrorReporter(DefaultErrorReporter reporter) {
        return reporter;
    }

    @Provides
    @Singleton
    ErrorEventSource provideErrorEventSource(DefaultErrorReporter reporter) {
        return reporter;
    }

    @Provides
    @Singleton
    AudioPlayerPort provideAudioPlayer() {
        return new JavaFxAudioPlayer();
    }

    @Provides
    @Singleton
    PeerTransportPort providePeerTransport() {
        return new KtorPeerTransport();
    }

    @Provides
    @Singleton
    ListeningSessionService provideListeningSessionService(
        AudioPlayerPort audioPlayer,
        PeerTransportPort transport,
        Clock clock,
        ScheduledExecutorService scheduler,
        ClockSynchronizer clockSynchronizer,
        ErrorReporter errorReporter
    ) {
        return new ListeningSessionService(audioPlayer, transport, clock, scheduler, clockSynchronizer, errorReporter);
    }
}
