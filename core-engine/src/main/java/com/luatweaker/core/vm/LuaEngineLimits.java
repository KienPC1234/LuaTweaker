package com.luatweaker.core.vm;

import org.jetbrains.annotations.NotNull;

/**
 * Hard resource caps applied to one luava {@code LuaState}.
 *
 * <p>Every value follows luava's own convention: {@code 0} means "keep the runtime default", so
 * {@link #UNLIMITED} reproduces the behaviour of an engine created before limits existed. A
 * positive value is enforced by the VM itself and surfaces as a Lua error instead of a dead
 * server (heap exhaustion, unbounded call frames, or a coroutine leak).</p>
 *
 * @param maxMemoryBytes heap cap for this VM in bytes; {@code 0} disables the cap
 * @param maxCallDepth   maximum simultaneous Lua call frames; {@code 0} keeps luava's default
 * @param maxCoroutines  maximum live Lua coroutines; {@code 0} disables the cap
 */
public record LuaEngineLimits(long maxMemoryBytes, int maxCallDepth, int maxCoroutines) {

    /** No cap: the engine behaves exactly like luava's defaults. */
    public static final LuaEngineLimits UNLIMITED = new LuaEngineLimits(0L, 0, 0);

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
    }

    /** @return {@code true} when at least one cap is enabled */
    public boolean isAnyLimitSet() {
        return maxMemoryBytes > 0 || maxCallDepth > 0 || maxCoroutines > 0;
    }

    /** @return a one-line description used by engine logs and diagnostics */
    public @NotNull String describe() {
        if (!isAnyLimitSet()) {
            return "unlimited";
        }
        return "maxMemoryBytes=" + maxMemoryBytes
                + ", maxCallDepth=" + maxCallDepth
                + ", maxCoroutines=" + maxCoroutines;
    }
}
