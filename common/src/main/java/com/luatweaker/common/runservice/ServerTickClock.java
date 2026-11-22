package com.luatweaker.common.runservice;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Wall-clock delta between server ticks. {@code RunService.Stepped/Heartbeat} report real
 * elapsed seconds clamped to one second, so a server stall does not hand Lua an absurd delta
 * (and a fast first tick does not report 0).
 *
 * <p>State is a single {@link AtomicLong} read-and-set, so no monitor is needed: the delta is
 * computed from the value this call observed, never from a shared mutable pair.</p>
 */
public final class ServerTickClock {
    private static final long NO_PREVIOUS_TICK = 0L;

    /** One Minecraft tick at the nominal 20 TPS. */
    public static final double NOMINAL_DELTA_SECONDS = 0.05;
    /** Upper bound for a reported delta; a longer stall is reported as one second. */
    private static final double MAX_DELTA_SECONDS = 1.0;
    /** Nanoseconds in one second, as a divisor for {@link System#nanoTime()}. */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private static final AtomicLong LAST_TICK_NANOS = new AtomicLong(NO_PREVIOUS_TICK);

    private ServerTickClock() {
    }

    /** Seconds since the previous call, clamped to [0.05, 1.0]. */
    public static double nextDelta() {
        long now = System.nanoTime();
        long previous = LAST_TICK_NANOS.getAndSet(now);
        if (previous == NO_PREVIOUS_TICK) {
            return NOMINAL_DELTA_SECONDS;
        }
        double delta = (now - previous) / NANOS_PER_SECOND;
        return Math.max(NOMINAL_DELTA_SECONDS, Math.min(MAX_DELTA_SECONDS, delta));
    }
}
