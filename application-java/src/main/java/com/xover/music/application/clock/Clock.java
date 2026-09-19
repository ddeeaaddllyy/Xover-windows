package com.xover.music.application.clock;

/**
 * Small abstraction over wall-clock time. Keeping it as a port makes sync logic
 * deterministic in tests.
 */
public interface Clock {
    long nowMillis();
}
