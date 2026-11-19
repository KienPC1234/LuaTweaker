package com.luatweaker.platform.runservice;

/**
 * Wall-clock delta between server ticks. {@code RunService.Stepped/Heartbeat} report real
 * elapsed seconds clamped to one second, so a server stall does not hand Lua an absurd delta
 * (and a fast first tick does not report 0).
 */
public final class ServerTickClock {

    /** One Minecraft tick at the nominal 20 TPS. */
    public static final double NOMINAL_DELTA_SECONDS = 0.05;
    /** Upper bound for a reported delta; a longer stall is reported as one second. */
    private static final double MAX_DELTA_SECONDS = 1.0;

    private static long lastTickNanos;

    private ServerTickClock() {
    }

    /** Seconds since the previous call, clamped to [0.05, 1.0]. */
    public static synchronized double nextDelta() {
        long now = System.nanoTime();
        if (lastTickNanos == 0L) {
            lastTickNanos = now;
            return NOMINAL_DELTA_SECONDS;
        }
        double delta = (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;
        return Math.max(NOMINAL_DELTA_SECONDS, Math.min(MAX_DELTA_SECONDS, delta));
    }
}
