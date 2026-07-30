package com.luatweaker.api.log;

/**
 * Platform-agnostic logger interface for all LuaTweaker subsystems.
 *
 * <h3>Extension guide</h3>
 * Replace {@link #get()} implementation with any backend (file, DB, network)
 * without touching call sites — every subsystem codes against this interface.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ILuaTweakerLog log = LuaTweakerLog.get();
 * log.info(LogStage.RECIPE_ADD, "Added shapeless recipe: my_mod:bread");
 * log.recipe(LogStage.RECIPE_REMOVE, "minecraft:cake", null, "Removed by Lua script");
 * }</pre>
 */
public interface ILuaTweakerLog {

    // ── Basic log levels ──────────────────────────────────────────────────

    void info(LogStage stage, String message);

    void warn(LogStage stage, String message);

    void error(LogStage stage, String message);

    void debug(LogStage stage, String message);

    // ── Script-aware variant (includes Lua file/line info) ────────────────

    /**
     * Log a message with Lua source location context.
     *
     * @param stage    The current pipeline stage.
     * @param message  The log message.
     * @param file     Lua file name (e.g. {@code "server/myrecipes.lua"}).
     * @param line     Lua source line number, or {@code -1} if unknown.
     */
    void script(LogStage stage, String message, String file, int line);

    // ── Recipe-specific structured logging ────────────────────────────────

    /**
     * Log a recipe operation with full before/after detail.
     *
     * @param stage        {@link LogStage#RECIPE_ADD}, {@link LogStage#RECIPE_REMOVE}, etc.
     * @param recipeId     The recipe identifier (may be null for wildcard removals).
     * @param outputItem   Output item registry name (e.g. {@code "minecraft:cake"}).
     * @param details      Human-readable description (e.g. "shapeless, 3 ingredients").
     */
    void recipe(LogStage stage, String recipeId, String outputItem, String details);

    /**
     * Emit a formatted separator / header to visually group a set of log lines.
     * E.g. called at the start of each script execution block.
     */
    void section(String title);

    // ── Stage lifecycle helpers ────────────────────────────────────────────

    /** Mark the beginning of a pipeline stage. */
    default void stageBegin(LogStage stage) {
        section("BEGIN " + stage.name());
    }

    /** Mark the successful end of a pipeline stage with optional timing. */
    default void stageEnd(LogStage stage, long elapsedMs) {
        info(stage, "Stage complete in " + elapsedMs + " ms");
    }
}
