package com.luatweaker.platform.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class LuaTweakerConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.BooleanValue AUTO_GENERATE_STUBS;
    public static final ModConfigSpec.BooleanValue SUPPRESS_EXPERIMENTAL_WARNING;

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
            .comment("Automatically accept Minecraft's 'experimental features' warnings when creating or opening a world with LuaTweaker datapacks")
            .define("suppressExperimentalWarning", true);

        builder.pop();
        COMMON_SPEC = builder.build();
    }
}
