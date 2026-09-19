package com.xover.music.app;

import com.xover.music.ui.DesktopApplication;

public final class XoverMain {
    private XoverMain() {
    }

    public static void main(String[] args) {
        AppComponent component = DaggerAppComponent.create();
        new DesktopApplication(component.listeningSessionService()).start();
    }
}
