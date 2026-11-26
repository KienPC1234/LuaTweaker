package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Shared argument readers and table converters for World environment Lua bindings. */
final class WorldEnvSupport {

    private WorldEnvSupport() {
    }

    record DimLong(String dimension, long value) {
    }

    record TwoCoords(String dimension, int x, int z, int cursor) {
    }

    static void bindPair(ILuaTable table, String pascalName, ILuaFunction fn) {
        table.rawset(pascalName, fn);
        String camel = Character.toLowerCase(pascalName.charAt(0)) + pascalName.substring(1);
        table.rawset(camel, fn);
    }

    @Nullable
    static String readOptionalDimension(ILuaValue[] args, int off) {
        if (args.length <= off || args[off].isNil()) {
            return InteractionBindingSupport.DEFAULT_DIMENSION;
        }
        if (!args[off].isString()) {
            return null;
        }
        String dim = args[off].asString().trim();
        return dim.isEmpty() ? null : dim;
    }

    static ILuaValue mapToLuaTable(ILuaEngine engine, @Nullable Map<String, Object> map) {
        if (map == null) {
            return engine.nilValue();
        }
        ILuaTable table = engine.createTable();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            table.rawset(entry.getKey(), InteractionLuaBinding.wrapValue(engine, entry.getValue()));
        }
        return table;
    }

    @Nullable
    static DimLong readDimAndLong(ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
            return null;
        }
        String dim = InteractionBindingSupport.DEFAULT_DIMENSION;
        int numIdx = off;
        if (args[off].isString()) {
            dim = args[off].asString().trim();
            if (dim.isEmpty() || !InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return null;
            }
            numIdx = off + 1;
        }
        if (!isFiniteNumber(args[numIdx])) {
            return null;
        }
        return new DimLong(dim, (long) Math.floor(args[numIdx].asDouble()));
    }

    @Nullable
    static TwoCoords readTwoCoords(ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
            return null;
        }
        String dim = InteractionBindingSupport.DEFAULT_DIMENSION;
        int idx = off;
        if (args[off].isString()) {
            dim = args[off].asString().trim();
            idx = off + 1;
        }
        if (dim.isEmpty() || idx >= args.length) {
            return null;
        }
        if (args[idx].isTable()) {
            return readTwoTableCoords(dim, args[idx].asTable(), idx + 1);
        }
        int needed = idx + 1;
        if (needed < args.length && isFiniteNumber(args[idx]) && isFiniteNumber(args[needed])) {
            int x = (int) Math.floor(args[idx].asDouble());
            int z = (int) Math.floor(args[needed].asDouble());
            return new TwoCoords(dim, x, z, needed + 1);
        }
        return null;
    }

    @Nullable
    private static TwoCoords readTwoTableCoords(String dim, ILuaTable table, int cursor) {
        Integer x = readTableAxis(table, InteractionBindingSupport.KEY_X, InteractionBindingSupport.KEY_X_LOWER);
        Integer z = readTableAxis(table, InteractionBindingSupport.KEY_Z, InteractionBindingSupport.KEY_Z_LOWER);
        return (x != null && z != null) ? new TwoCoords(dim, x, z, cursor) : null;
    }

    static boolean isFiniteNumber(@Nullable ILuaValue value) {
        return value != null && value.isNumber() && Double.isFinite(value.asDouble());
    }

    @Nullable
    private static Integer readTableAxis(ILuaTable table, String upperKey, String lowerKey) {
        ILuaValue val = table.rawget(upperKey);
        if (val == null || val.isNil()) {
            val = table.rawget(lowerKey);
        }
        if (!isFiniteNumber(val)) {
            return null;
        }
        return (int) Math.floor(val.asDouble());
    }

    static String normalizeOrDefault(ILuaValue[] args, int cursor, String defaultVal) {
        if (cursor >= args.length || args[cursor].isNil()) {
            return defaultVal;
        }
        if (!args[cursor].isString()) {
            return "";
        }
        String trimmed = args[cursor].asString().trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? defaultVal : trimmed;
    }
}
