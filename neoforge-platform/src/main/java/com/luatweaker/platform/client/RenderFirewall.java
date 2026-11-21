package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Single firewall around client render hooks (camera, FOV, HUD/overlay passes).
 *
 * <p><b>WHY THE CATCH IS BROAD:</b> these hooks run inside Minecraft's render pipeline. A
 * failure here must never abort the frame (that black-screens the game), and the failure can be
 * an {@link Error} rather than an exception: a Lua-driven effect that recurses, or a render
 * pipeline that is half-initialised (resource reload mid-frame) surfaces as
 * {@code StackOverflowError} or {@code LinkageError}. Narrowing the catch would turn those into
 * hard crashes, so the breadth is intentional and lives in exactly one place instead of being
 * copied into every hook.</p>
 *
 * <p>Call sites that can name a precise failure (validation, parsing) must not use this
 * helper.</p>
 */
final class RenderFirewall {

    private RenderFirewall() {
    }

    /**
     * Runs one render-hook body; a failure is logged and swallowed so the frame continues.
     *
     * @param body    hook body
     * @param message lazy failure description (runs only on failure)
     */
    static void run(@NotNull Runnable body, @NotNull Supplier<String> message) {
        execute(body, message, false);
    }

    /** Same firewalls as {@link #run} but reports at WARN: used for failures that lose work. */
    static void runLoud(@NotNull Runnable body, @NotNull Supplier<String> message) {
        execute(body, message, true);
    }

    private static void execute(@NotNull Runnable body, @NotNull Supplier<String> message, boolean loud) {
        try {
            body.run();
        } catch (Throwable failure) {
            if (loud) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM, message.get() + ": " + failure.getMessage());
            } else {
                LuaTweakerLog.get().debug(LogStage.SYSTEM, message.get() + ": " + failure.getMessage());
            }
        }
    }
}
