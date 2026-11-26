package com.luatweaker.core.vm;

import org.jetbrains.annotations.NotNull;

/**
 * Hard resource caps applied to one luava {@code LuaState}.
 *
 * <p>Every numeric value follows luava's own convention: {@code 0} means "keep the runtime default",
 * so {@link #UNLIMITED} reproduces the behaviour of an engine created without limits. A positive
 * value is enforced by the VM itself and surfaces as a Lua error instead of a dead server (heap
 * exhaustion, unbounded call frames, coroutine leaks, or runaway loops).</p>
 *
 * @param maxMemoryBytes     retained Lua heap cap in bytes; {@code 0} disables the cap
 * @param maxCallDepth       maximum simultaneous Lua call frames; {@code 0} keeps luava's default
 * @param maxCoroutines      maximum live Lua coroutines; {@code 0} disables the cap
 * @param instructionLimit   per-entry VM instruction budget; {@code 0} disables the cap
 * @param timeoutMillis      per-entry wall-clock timeout in milliseconds; {@code 0} disables the cap
 * @param maxAllocationBytes per-entry stdlib allocation cap in bytes; {@code 0} disables the cap
 * @param cancellable        whether cooperative host cancellation is armed (supported in JIT too)
 */
public record LuaEngineLimits(
        long maxMemoryBytes,
        int maxCallDepth,
        int maxCoroutines,
        long instructionLimit,
        long timeoutMillis,
        long maxAllocationBytes,
        boolean cancellable) {

    /** No cap: the engine behaves exactly like luava's defaults. */
    public static final LuaEngineLimits UNLIMITED = new LuaEngineLimits(0L, 0, 0);

    /** Backwards-compatible constructor using defaults for instruction, timeout, alloc, and cancel flags. */
    public LuaEngineLimits(long maxMemoryBytes, int maxCallDepth, int maxCoroutines) {
        this(maxMemoryBytes, maxCallDepth, maxCoroutines, 0L, 0L, 0L, false);
    }

    public LuaEngineLimits {
        if (maxMemoryBytes < 0) {
            throw new IllegalArgumentException(
                    "maxMemoryBytes must be >= 0 (0 disables the cap), got " + maxMemoryBytes);
        }
        if (maxCallDepth < 0) {
            throw new IllegalArgumentException(
                    "maxCallDepth must be >= 0 (0 keeps the runtime default), got " + maxCallDepth);
        }
        if (maxCoroutines < 0) {
            throw new IllegalArgumentException(
                    "maxCoroutines must be >= 0 (0 disables the cap), got " + maxCoroutines);
        }
        if (instructionLimit < 0) {
            throw new IllegalArgumentException(
                    "instructionLimit must be >= 0 (0 disables the cap), got " + instructionLimit);
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "timeoutMillis must be >= 0 (0 disables the cap), got " + timeoutMillis);
        }
        if (maxAllocationBytes < 0) {
            throw new IllegalArgumentException(
                    "maxAllocationBytes must be >= 0 (0 disables the cap), got " + maxAllocationBytes);
        }
    }

    /** @return {@code true} when at least one cap or cancellation guard is enabled */
    public boolean isAnyLimitSet() {
        return maxMemoryBytes > 0
                || maxCallDepth > 0
                || maxCoroutines > 0
                || instructionLimit > 0
                || timeoutMillis > 0
                || maxAllocationBytes > 0
                || cancellable;
    }

    /** @return a one-line description used by engine logs and diagnostics */
    public @NotNull String describe() {
        if (!isAnyLimitSet()) {
            return "unlimited";
        }
        String base = "maxMemoryBytes=" + maxMemoryBytes
                + ", maxCallDepth=" + maxCallDepth
                + ", maxCoroutines=" + maxCoroutines;
        if (instructionLimit == 0 && timeoutMillis == 0 && maxAllocationBytes == 0 && !cancellable) {
            return base;
        }
        return base
                + ", instructionLimit=" + instructionLimit
                + ", timeoutMillis=" + timeoutMillis
                + ", maxAllocationBytes=" + maxAllocationBytes
                + ", cancellable=" + cancellable;
    }
}
