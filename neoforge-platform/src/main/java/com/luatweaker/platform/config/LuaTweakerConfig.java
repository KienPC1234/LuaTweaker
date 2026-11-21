package com.luatweaker.platform.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LuaTweakerConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.BooleanValue AUTO_GENERATE_STUBS;
    public static final ModConfigSpec.BooleanValue SUPPRESS_EXPERIMENTAL_WARNING;

    /**
     * STARTUP config: NeoForge loads this type synchronously inside
     * {@code ModContainer.registerConfig}, i.e. before the mod constructor returns. That is the
     * only window early enough to cap the VM that loads LuaMods during construction, which is why
     * these knobs cannot live in {@link #COMMON_SPEC} (COMMON is loaded after construction).
     */
    public static final ModConfigSpec ENGINE_SPEC;
    public static final ModConfigSpec.LongValue MAX_MEMORY_BYTES;
    public static final ModConfigSpec.IntValue MAX_CALL_DEPTH;
    public static final ModConfigSpec.IntValue MAX_COROUTINES;

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("LuaTweaker Engine Configuration Settings").push("general");

        DEBUG = builder
            .comment("Enable verbose debug logs and detailed traceback execution in latest.log")
            .define("debug", false);

        AUTO_GENERATE_STUBS = builder
            .comment("Automatically export EmmyLua stubs (.luatweaker/stubs) for IDE autocompletion")
            .define("autoGenerateStubs", true);

        SUPPRESS_EXPERIMENTAL_WARNING = builder
            .comment("Automatically accept Minecraft's 'experimental features' warnings when "
                + "creating or opening a world with LuaTweaker datapacks")
            .define("suppressExperimentalWarning", true);

        builder.pop();
        COMMON_SPEC = builder.build();

        ModConfigSpec.Builder engine = new ModConfigSpec.Builder();
        engine.comment(
                "Hard resource caps enforced by the Lua VM itself.",
                "These values are read before any LuaMod is loaded, so they also cover the",
                "startup load phase. 0 disables a cap (keeps the runtime default).",
                "A tripped cap raises a normal Lua error instead of exhausting the JVM heap.",
                "Takes effect after a game restart.")
            .push("limits");

        MAX_MEMORY_BYTES = engine
            .comment(
                "Heap budget for one Lua VM, in bytes (e.g. 268435456 = 256 MiB).",
                "Disabled by default: large worldgen/noise scripts legitimately allocate a lot,",
                "and a too-small cap would surface as out-of-memory errors inside those scripts.",
                "Set this to a value above your heaviest mod's peak usage.")
            .defineInRange("maxMemoryBytes", 0L, 0L, 8L * GIB);

        MAX_CALL_DEPTH = engine
            .comment(
                "Maximum simultaneous Lua call frames. 0 disables the cap.",
                "The runtime default is 600000, which is high enough that a runaway recursion",
                "can exhaust the heap before it is stopped.",
                "TRADE-OFF: luava disables its JIT for the whole VM while a call-depth cap is",
                "active, so enabling this costs execution speed. Leave it at 0 unless you are",
                "diagnosing runaway recursion.")
            .defineInRange("maxCallDepth", 0, 0, 600_000);

        MAX_COROUTINES = engine
            .comment(
                "Maximum live Lua coroutines. 0 disables the cap.",
                "Deferred tasks (task.spawn/task.delay) each own a coroutine, so a mod that",
                "spawns per tick can accumulate many suspended ones before they are pruned.")
            .defineInRange("maxCoroutines", 0, 0, 1_000_000);

        engine.pop();
        ENGINE_SPEC = engine.build();
    }

    private LuaTweakerConfig() {
    }
}
