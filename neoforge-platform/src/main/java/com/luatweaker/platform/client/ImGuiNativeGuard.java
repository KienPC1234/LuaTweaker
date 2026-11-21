package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Single firewall around {@code imgui-java} native calls.
 *
 * <p><b>WHY THE CATCH IS BROAD:</b> the imgui-java binding reports every native failure
 * (assert, corrupted ImGui stack, missing frame) as a plain unchecked exception; the library
 * defines no dedicated exception type to name here. Lua scripts can leave ImGui stacks open or
 * pass bad payload classes, so letting one of those escape would abort the whole frame and leak
 * the remaining stacks. Centralising the catch keeps that trade-off explicit and auditable
 * instead of spreading {@code catch (RuntimeException)} over every binding call.</p>
 *
 * <p>Callers that can name a precise failure (validation, parsing, IO) must not use this
 * helper.</p>
 */
final class ImGuiNativeGuard {

    private ImGuiNativeGuard() {
    }

    /**
     * Runs one native call. The failure handler decides how loud the report is and whether the
     * caller should stop working on the same structure.
     *
     * @return {@code true} when the call completed
     */
    static boolean run(@NotNull Runnable call, @NotNull Consumer<RuntimeException> onFailure) {
        return Boolean.TRUE.equals(supply(() -> {
            call.run();
            return Boolean.TRUE;
        }, null, onFailure));
    }

    /**
     * Runs one value-returning native call.
     *
     * @param body     native call
     * @param fallback value returned when the call fails
     * @param onFailure failure handler (logging)
     * @return the call result, or {@code fallback} after a handled failure
     */
    static <T> @Nullable T supply(@NotNull Supplier<T> body, @Nullable T fallback,
                                  @NotNull Consumer<RuntimeException> onFailure) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            onFailure.accept(e);
            return fallback;
        }
    }

    /** Shared debug-logging failure handler for best-effort ImGui calls. */
    static @NotNull Consumer<RuntimeException> debugLogger(@NotNull Supplier<String> message) {
        return failure -> LuaTweakerLog.get().debug(LogStage.SYSTEM, message.get() + ": " + failure.getMessage());
    }
}
