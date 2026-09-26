package com.jose.subscriptions.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que los tests pueden mover a voluntad para simular el paso de días o meses. */
public class MutableClock extends Clock {

    public static final Instant DEFAULT_START = Instant.parse("2026-01-01T00:00:00Z");

    private volatile Instant now = DEFAULT_START;

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    public void reset() {
        this.now = DEFAULT_START;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
