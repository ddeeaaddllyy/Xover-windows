package com.xover.music.application.common.diagnostics;

public interface ErrorReporter {
    void report(String operation, Throwable failure);

    default void report(Throwable failure) {
        report("", failure);
    }
}
