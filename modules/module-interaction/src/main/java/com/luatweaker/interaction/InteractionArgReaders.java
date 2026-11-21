package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaValue;

import java.util.Locale;

/** Scalar Lua call-argument readers: sides, ints, strings and dimension prefixes. */
final class InteractionArgReaders {

    /** Mirrors {@code net.minecraft.core.Direction}; used only to validate Lua input. */
    private static final java.util.Set<String> SIDE_NAMES = java.util.Set.of(
            "UP", "DOWN", "NORTH", "SOUTH", "WEST", "EAST");

    private InteractionArgReaders() {
    }

    static InteractionBindingSupport.SideParse readSide(ILuaValue[] args, int index, int length) {
        if (index >= length) {
            return new InteractionBindingSupport.SideParse(false, null);
        }
        ILuaValue value = args[index];
        if (value == null || !value.isString()) {
            return new InteractionBindingSupport.SideParse(true, null);
        }
        String raw = value.asString().trim();
        if (raw.isEmpty() || "NONE".equalsIgnoreCase(raw) || "NULL".equalsIgnoreCase(raw)) {
            return new InteractionBindingSupport.SideParse(false, null);
        }
        String name = raw.toUpperCase(Locale.ROOT);
        return SIDE_NAMES.contains(name)
                ? new InteractionBindingSupport.SideParse(false, name)
                : new InteractionBindingSupport.SideParse(true, null);
    }

    static Integer readIntArg(ILuaValue[] args, int index, int length) {
        if (index >= length || args[index] == null || !args[index].isNumber()) {
            return null;
        }
        return args[index].asInt();
    }

    static String readStringArg(ILuaValue[] args, int index, int length) {
        if (index >= length || args[index] == null || !args[index].isString()) {
            return null;
        }
        return args[index].asString();
    }

    /**
     * A leading dimension is only possible when the first two arguments are strings
     * (dimension, then id); a position can never be a string.
     */
    static int resolveDimensionPrefix(ILuaValue[] args, int off) {
        if (InteractionBindingSupport.hasMinArgs(args, off, 2)
                && args[off].toJavaObject() instanceof String
                && args[off + 1].toJavaObject() instanceof String) {
            return off;
        }
        return -1;
    }

    /** Same as {@link #resolveDimensionPrefix}, but the second string may be a sound category. */
    static int resolveStopSoundDimensionPrefix(ILuaValue[] args, int off) {
        if (InteractionBindingSupport.hasMinArgs(args, off, 2)
                && args[off].toJavaObject() instanceof String
                && args[off + 1].toJavaObject() instanceof String
                && !InteractionBindingSupport.SOUND_SOURCE_NAMES.contains(
                        args[off + 1].asString().trim().toUpperCase(Locale.ROOT))) {
            return off;
        }
        return -1;
    }
}
