package com.luatweaker.platform.util;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single choke point for broad catches around Lua callbacks and mod-introspection.
 *
 * <p>WHY BROAD: Lua scripts (and the game/mods they touch) can throw ANY unchecked
 * throwable — script errors surface as {@code RuntimeException} subclasses from the Lua
 * VM, and event objects may be half-built when a handler runs. A firewall that only
 * caught specific types would let one bad Lua listener kill the server tick, the
 * render thread, or mod loading. Centralizing here keeps that trade-off explicit and
 * auditable instead of scattering {@code catch (Exception)} blocks across call sites.
 *
 * <p>Callers that CAN name the precise failure (IO, parsing, missing config) must
 * catch that specific exception type at the call site instead of using this helper.
 */
public final class CallbackFirewall {
    private CallbackFirewall() {
    }

    /**
     * Runs a Lua-touching action; Lua failures are logged and swallowed so the
     * calling thread (tick, render, network) survives them. The message supplier
     * runs lazily so building it cannot throw outside the firewall.
     */
    public static void run(
            @NotNull Runnable action,
            @NotNull LogStage stage,
            @NotNull java.util.function.Supplier<String> message) {
        try {
            action.run();
        } catch (Exception e) {
            LuaTweakerLog.get().warn(stage, message.get() + ": " + e.getMessage());
        }
    }

    /**
     * Runs a Lua-touching query; Lua failures are logged and the fallback is
     * returned so callers keep a total function contract.
     */
    public static <T> T supply(
            @NotNull java.util.function.Supplier<T> action,
            @Nullable T fallback,
            @NotNull LogStage stage,
            @NotNull java.util.function.Supplier<String> message) {
        try {
            return action.get();
        } catch (Exception e) {
            LuaTweakerLog.get().warn(stage, message.get() + ": " + e.getMessage());
            return fallback;
        }
    }
}
