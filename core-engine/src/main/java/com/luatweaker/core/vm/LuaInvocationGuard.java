package com.luatweaker.core.vm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Single firewall around every Lua entry point.
 *
 * <p><b>Why the catch is deliberately broad:</b> a Lua script must never be able to
 * tear down the server or render thread. luava reports host-binding failures as
 * whatever the binding threw, and a runaway recursive script surfaces as
 * {@link StackOverflowError} - an {@link Error}, not an {@link Exception}. Narrowing
 * this to {@code Exception} would turn those script bugs into hard crashes, so the
 * breadth is intentional and lives in exactly one place instead of being copied into
 * every call site.</p>
 *
 * <p>Call sites keep their own recovery policy (record the error, return an empty
 * result, or rethrow a wrapped exception) through {@code onFailure}.</p>
 */
final class LuaInvocationGuard {
    private LuaInvocationGuard() {}

    /** Body executed under the firewall; may return a value. */
    @FunctionalInterface
    interface GuardedBody<T> {
        @Nullable T run();
    }

    /**
     * Runs {@code body}; if anything escapes, hands the failure to {@code onFailure}
     * and returns {@code fallback}. {@code onFailure} may rethrow to convert the
     * failure into a domain exception.
     *
     * @param body      Lua operation to execute
     * @param fallback  value returned when the body fails and the handler returns
     * @param onFailure recovery policy, called with the escaping failure
     * @return the body result, or {@code fallback} after a handled failure
     */
    static <T> @Nullable T call(@NotNull GuardedBody<T> body, @Nullable T fallback,
                                @NotNull Consumer<Throwable> onFailure) {
        try {
            return body.run();
        } catch (Throwable failure) {
            onFailure.accept(failure);
            return fallback;
        }
    }
}
