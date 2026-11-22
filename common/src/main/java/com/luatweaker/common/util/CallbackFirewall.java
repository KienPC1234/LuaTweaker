package com.luatweaker.common.util;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Single choke point for broad catches around Lua callbacks and mod-introspection.
 */
public final class CallbackFirewall {
    private CallbackFirewall() {
    }

    public static void run(
            @NotNull Runnable action,
            @NotNull LogStage stage,
            @NotNull Supplier<String> message) {
        try {
            action.run();
        } catch (Exception | LinkageError | AssertionError e) {
            LuaTweakerLog.get().warn(stage, message.get() + ": " + e.getMessage());
        }
    }

    public static <T> T supply(
            @NotNull Supplier<T> action,
            @Nullable T fallback,
            @NotNull LogStage stage,
            @NotNull Supplier<String> message) {
        try {
            return action.get();
        } catch (Exception | LinkageError | AssertionError e) {
            LuaTweakerLog.get().warn(stage, message.get() + ": " + e.getMessage());
            return fallback;
        }
    }
}
