package com.xover.music.application.sync;

/**
 * Estimates host time from client time by using a standard midpoint sample:
 * <p>
 * offset = hostMidpoint - clientMidpoint
 */
public final class ClockSynchronizer {
    private volatile long offsetMillis;
    private volatile boolean synchronizedOnce;

    public void applySample(
        long clientSentAtMillis,
        long clientReceivedAtMillis,
        long hostReceivedAtMillis,
        long hostSentAtMillis
    ) {
        long clientMidpoint = midpoint(clientSentAtMillis, clientReceivedAtMillis);
        long hostMidpoint = midpoint(hostReceivedAtMillis, hostSentAtMillis);
        long sample = hostMidpoint - clientMidpoint;

        if (!synchronizedOnce) {
            offsetMillis = sample;
            synchronizedOnce = true;
            return;
        }

        offsetMillis = Math.round(offsetMillis * 0.7 + sample * 0.3);
    }

    public long offsetMillis() {
        return offsetMillis;
    }

    public long hostTimeFromLocal(long localTimeMillis) {
        return localTimeMillis + offsetMillis;
    }

    public long localDelayUntilHostTime(long hostTimestampMillis, long localNowMillis) {
        return Math.max(0L, hostTimestampMillis - hostTimeFromLocal(localNowMillis));
    }

    private long midpoint(long first, long second) {
        return first + ((second - first) / 2L);
    }
}
