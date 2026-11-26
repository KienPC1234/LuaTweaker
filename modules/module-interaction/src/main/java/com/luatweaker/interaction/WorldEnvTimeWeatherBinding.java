package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.Map;

/** Binds World time and weather primitives (GetDayTime, SetDayTime, GetGameTime, GetWeather, SetWeather, ...). */
final class WorldEnvTimeWeatherBinding {

    private static final int DURATION_ARG_INDEX = 2;
    private static final double MIN_DURATION_TICKS = 0.0;

    private WorldEnvTimeWeatherBinding() {
    }

    static void bind(ILuaEngine engine, ILuaTable table) {
        bindTime(engine, table);
        bindWeather(engine, table);
    }

    private static void bindTime(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetDayTime", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            long time = Platform.getInteraction().getDayTime(dim);
            return time >= 0L ? engine.wrapNumber(time) : engine.nilValue();
        });

        WorldEnvSupport.bindPair(table, "SetDayTime", args -> {
            WorldEnvSupport.DimLong parsed = WorldEnvSupport.readDimAndLong(args);
            if (parsed == null || parsed.value() < 0L) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(Platform.getInteraction().setDayTime(parsed.dimension(), parsed.value()));
        });

        WorldEnvSupport.bindPair(table, "GetGameTime", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            long time = Platform.getInteraction().getGameTime(dim);
            return time >= 0L ? engine.wrapNumber(time) : engine.nilValue();
        });
    }

    private static void bindWeather(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetWeather", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            return WorldEnvSupport.mapToLuaTable(engine, Platform.getInteraction().getWeather(dim));
        });

        WorldEnvSupport.bindPair(table, "IsRaining", args ->
                engine.wrapBoolean(readWeatherFlag(args, "Raining")));

        WorldEnvSupport.bindPair(table, "IsThundering", args ->
                engine.wrapBoolean(readWeatherFlag(args, "Thundering")));

        WorldEnvSupport.bindPair(table, "SetWeather", args ->
                engine.wrapBoolean(handleSetWeather(args)));
    }

    private static boolean readWeatherFlag(ILuaValue[] args, String key) {
        String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
        if (dim == null) {
            return false;
        }
        Map<String, Object> weather = Platform.getInteraction().getWeather(dim);
        return weather != null && Boolean.TRUE.equals(weather.get(key));
    }

    private static boolean handleSetWeather(ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        if (!InteractionBindingSupport.hasMinArgs(args, off, 1) || !args[off].isString()) {
            return false;
        }
        String first = args[off].asString().trim();
        if (first.isEmpty()) {
            return false;
        }
        if (!InteractionBindingSupport.hasMinArgs(args, off, DURATION_ARG_INDEX) || args[off + 1].isNil()) {
            return Platform.getInteraction().setWeather(InteractionBindingSupport.DEFAULT_DIMENSION, first, 0);
        }
        ILuaValue second = args[off + 1];
        if (second.isString()) {
            return applyExplicitDimWeather(first, second.asString().trim(), args, off + DURATION_ARG_INDEX);
        }
        Integer duration = parseOptionalDuration(args, off + 1);
        return duration != null
                && Platform.getInteraction().setWeather(InteractionBindingSupport.DEFAULT_DIMENSION, first, duration);
    }

    private static boolean applyExplicitDimWeather(String dim, String kind, ILuaValue[] args, int durationIdx) {
        Integer duration = parseOptionalDuration(args, durationIdx);
        return !kind.isEmpty() && duration != null && Platform.getInteraction().setWeather(dim, kind, duration);
    }

    private static Integer parseOptionalDuration(ILuaValue[] args, int index) {
        if (index >= args.length || args[index].isNil()) {
            return 0;
        }
        if (!WorldEnvSupport.isFiniteNumber(args[index]) || args[index].asDouble() < MIN_DURATION_TICKS) {
            return null;
        }
        return (int) Math.floor(args[index].asDouble());
    }
}
