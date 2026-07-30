package com.luatweaker.api.log;

/**
 * Every major pipeline stage in LuaTweaker has an enum constant here.
 * This makes it easy to filter or format log output by stage, both in files
 * and in any future in-game debug overlay.
 *
 * <pre>
 * Lifecycle order:
 *   MOD_INIT → ENGINE_INIT → SCRIPT_LOAD → [RECIPE_* events] → APPLY → DONE
 * </pre>
 */
public enum LogStage {
    // ── Startup ────────────────────────────────────────────────────────────
    /** Mod constructor running. Platform PAL / config init. */
    MOD_INIT,
    /** Cobalt VM / engine startup. */
    ENGINE_INIT,
    /** LSP stub generation. */
    STUB_GEN,

    // ── Script execution ───────────────────────────────────────────────────
    /** A Lua script file is being loaded and executed. */
    SCRIPT_LOAD,
    /** A Lua syntax error was detected (via /lt syntax or during load). */
    SYNTAX_ERROR,
    /** A Lua runtime error was thrown. */
    RUNTIME_ERROR,

    // ── Recipe pipeline ────────────────────────────────────────────────────
    /** A recipe was removed by output, input, or ID. */
    RECIPE_REMOVE,
    /** A shaped or shapeless recipe was added. */
    RECIPE_ADD,
    /** An input or output item was replaced across existing recipes. */
    RECIPE_REPLACE,
    /** Recipe modifications are being applied to the Minecraft RecipeManager. */
    RECIPE_APPLY,

    // ── Commands ───────────────────────────────────────────────────────────
    /** A /luatweaker command was executed. */
    COMMAND,

    // ── General ───────────────────────────────────────────────────────────
    /** Server reload triggered. */
    RELOAD,
    /** Miscellaneous system message not tied to a specific stage. */
    SYSTEM
}
