package com.stablebridge.indexer.testutil;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A mutable {@link Clock} implementation for testing time-dependent behavior.
 *
 * <p>Allows tests to control the flow of time by advancing or setting the instant,
 * useful for testing health probes, retry intervals, and timeout logic.
 */
public final class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneId.of("UTC"));
    }

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
