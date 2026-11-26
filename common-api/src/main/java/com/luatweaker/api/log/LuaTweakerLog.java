package com.luatweaker.api.log;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Global accessor for the active {@link ILuaTweakerLog} implementation.
 *
 * <p>The platform bootstrap ({@code LuaTweakerCommon}) calls {@link #set(ILuaTweakerLog)}
 * during mod init. All subsystems then call {@link #get()} freely - never
 * importing a concrete class.</p>
 *
 * <p>If no implementation has been registered, a {@link NoOpLog} is returned
 * so call sites never need to null-check.</p>
 */
public final class LuaTweakerLog {

    private static final AtomicReference<ILuaTweakerLog> INSTANCE =
            new AtomicReference<>(new NoOpLog());

    private LuaTweakerLog() {}

    /** Register the concrete logger implementation (called once at mod init). */
    public static void set(ILuaTweakerLog logger) {
        INSTANCE.set(logger);
    }

    /** @return the active log implementation, never {@code null}. */
    public static ILuaTweakerLog get() {
        return INSTANCE.get();
    }

    // -- No-op fallback ---

    /** Silent logger used before the real one is installed. */
    private static final class NoOpLog implements ILuaTweakerLog {
        @Override public void info(LogStage stage, String message) {}
        @Override public void warn(LogStage stage, String message) {}
        @Override public void error(LogStage stage, String message) {}
        @Override public void debug(LogStage stage, String message) {}
        @Override public void script(LogStage stage, String message, String file, int line) {}
        @Override public void recipe(LogStage stage, String recipeId, String outputItem, String details) {}
        @Override public void section(String title) {}
    }
}
