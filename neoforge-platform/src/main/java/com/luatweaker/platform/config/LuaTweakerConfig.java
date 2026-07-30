package com.luatweaker.platform.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class LuaTweakerConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.BooleanValue AUTO_GENERATE_STUBS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("LuaTweaker Engine Configuration Settings").push("general");

        DEBUG = builder
            .comment("Enable verbose debug logs and detailed traceback execution in latest.log")
            .define("debug", false);

        AUTO_GENERATE_STUBS = builder
            .comment("Automatically export EmmyLua stubs (.luatweaker/stubs) for IDE autocompletion")
            .define("autoGenerateStubs", true);

        builder.pop();
        COMMON_SPEC = builder.build();
    }
}
