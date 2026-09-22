package com.xover.music.app;

import com.xover.music.application.common.diagnostics.ErrorReporter;
import com.xover.music.ui.DesktopApplication;

public final class XoverMain {
    private XoverMain() {
    }

    public static void main(String[] args) {
        AppComponent component = DaggerAppComponent.create();
        ErrorReporter errorReporter = component.errorReporter();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
            errorReporter.report("Uncaught error on " + thread.getName(), failure)
        );
        new DesktopApplication(
            component.listeningSessionService(),
            component.errorEventSource(),
            errorReporter
        ).start();
    }
}
