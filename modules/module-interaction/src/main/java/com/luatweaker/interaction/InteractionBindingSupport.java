package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformCapability;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;

/** Shared glue for the interaction binding collaborators: arg checks, keys, warnings, capabilities. */
final class InteractionBindingSupport {

    static final String DEFAULT_DIMENSION = "minecraft:overworld";
    /** Historical default for world sounds: category-level volume follows the Players slider. */
    static final String DEFAULT_SOUND_SOURCE = "PLAYERS";
    /** Mirrors {@code net.minecraft.sounds.SoundSource}; used only to disambiguate StopSound args. */
    static final java.util.Set<String> SOUND_SOURCE_NAMES = java.util.Set.of(
            "MASTER", "MUSIC", "RECORDS", "WEATHER", "BLOCKS", "HOSTILE", "NEUTRAL", "PLAYERS",
            "AMBIENT", "VOICE");

    static final String METHOD_GET_ENTITY = "GetEntity";
    static final String METHOD_PLAY_ANIMATION = "PlayAnimation";
    static final String METHOD_STOP_ANIMATION = "StopAnimation";
    static final String METHOD_GET_ATTRIBUTE = "GetAttribute";
    static final String METHOD_SET_ATTRIBUTE = "SetAttribute";
    static final String CTRL_DEFAULT = "controller";
    static final String NEW_METHOD = "new";

    static final String ENTITY_MARKER = "__entity";
    static final String ITEM_MARKER = "__item";
    static final String BLOCK_MARKER = "__block";
    static final String BLOCK_KEY = "block";
    static final String BLOCK_PROP = "Block";

    static final String KEY_X = "X";
    static final String KEY_X_LOWER = "x";
    static final String KEY_Y = "Y";
    static final String KEY_Y_LOWER = "y";
    static final String KEY_Z = "Z";
    static final String KEY_Z_LOWER = "z";
    static final String KEY_DIMENSION = "dimension";
    static final String KEY_DIMENSION_UPPER = "Dimension";

    private InteractionBindingSupport() {
    }

    record CoordinateArgs(String dimension, int x, int y, int z, int cursor) {
    }

    record SideParse(boolean error, String name) {
    }

    static boolean hasMinArgs(ILuaValue[] args, int off, int min) {
        return args.length - off >= min;
    }

    static void warn(String message) {
        com.luatweaker.api.log.LuaTweakerLog.get().warn(
                com.luatweaker.api.log.LogStage.SYSTEM, message);
    }

    static void warnSide(String call) {
        warn(call + " side must be one of UP, DOWN, NORTH, SOUTH, WEST, EAST");
    }

    static void warnExtraArgs(String call, int cursor, int length) {
        if (cursor < length) {
            warn(call + " ignored " + (length - cursor) + " extra argument(s) starting at #" + (cursor + 1));
        }
    }

    static IPlatformCapability capability(boolean... warned) {
        IPlatformCapability capability = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        if (capability == null && !warned[0]) {
            warned[0] = true;
            warn("World capability access is unavailable on this platform");
        }
        return capability;
    }

    @org.jetbrains.annotations.Nullable
    static ILuaValue getDynamicFallback(@org.jetbrains.annotations.NotNull ILuaEngine engine,
            @org.jetbrains.annotations.NotNull Object rawJavaObj,
            @org.jetbrains.annotations.NotNull String key) {
        return com.luatweaker.entities.EntitiesLuaBinding.readDynamicProperty(engine, rawJavaObj, key);
    }

    static void setDynamicFallback(@org.jetbrains.annotations.NotNull ILuaEngine engine,
            @org.jetbrains.annotations.NotNull Object rawJavaObj,
            @org.jetbrains.annotations.NotNull String key,
            @org.jetbrains.annotations.NotNull ILuaValue val) {
        com.luatweaker.entities.EntitiesLuaBinding.writeDynamicProperty(engine, rawJavaObj, key, val);
    }

    static void fireAttributeChanged(ILuaEngine engine, ILuaValue attrSignal, String name, String value) {
        if (attrSignal == null || attrSignal.isNil()) {
            return;
        }
        ILuaValue fireFn = attrSignal.asTable().rawget("Fire");
        if (fireFn == null || fireFn.isNil()) {
            return;
        }
        engine.callFunction(fireFn, attrSignal, engine.wrapString(name), engine.wrapString(value));
    }
}
