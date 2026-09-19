package com.xover.music.application.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClockSynchronizerTest {
    @Test
    void estimatesHostOffsetFromMidpoints() {
        ClockSynchronizer synchronizer = new ClockSynchronizer();

        synchronizer.applySample(
            1_000L,
            1_100L,
            1_250L,
            1_350L
        );

        assertEquals(250L, synchronizer.offsetMillis());
        assertEquals(1_750L, synchronizer.hostTimeFromLocal(1_500L));
    }

    @Test
    void calculatesDelayUntilHostTimestamp() {
        ClockSynchronizer synchronizer = new ClockSynchronizer();
        synchronizer.applySample(
            1_000L,
            1_020L,
            1_200L,
            1_220L
        );

        assertEquals(190L, synchronizer.localDelayUntilHostTime(1_400L, 1_010L));
        assertEquals(0L, synchronizer.localDelayUntilHostTime(1_100L, 1_010L));
    }
}
