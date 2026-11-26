package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.Nullable;

/** Binds World metadata, border, and GameRule primitives (GetDifficulty, GetSeed, GetWorldBorder, SetWorldBorder, GetGameRule, SetGameRule). */
final class WorldEnvMetaBinding {

    private static final int MIN_BORDER_NUMBERS = 3;
    private static final int GAMERULE_SET_MIN_ARGS = 2;
    private static final double MIN_LERP_MILLIS = 0.0;

    private WorldEnvMetaBinding() {
    }

    static void bind(ILuaEngine engine, ILuaTable table) {
        bindDifficultyAndSeed(engine, table);
        bindBorder(engine, table);
        bindGameRules(engine, table);
    }

    private static void bindDifficultyAndSeed(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetDifficulty", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            String difficulty = Platform.getInteraction().getDifficulty(dim);
            return difficulty != null ? engine.wrapString(difficulty) : engine.nilValue();
        });

        WorldEnvSupport.bindPair(table, "GetSeed", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            return engine.wrapNumber(Platform.getInteraction().getSeed(dim));
        });
    }

    private static void bindBorder(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetWorldBorder", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            return WorldEnvSupport.mapToLuaTable(engine, Platform.getInteraction().getWorldBorder(dim));
        });

        WorldEnvSupport.bindPair(table, "SetWorldBorder", args ->
                engine.wrapBoolean(handleSetWorldBorder(args)));
    }

    private static void bindGameRules(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetGameRule", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1) || !args[off].isString()) {
                return engine.nilValue();
            }
            String rule = args[off].asString().trim();
            if (rule.isEmpty()) {
                return engine.nilValue();
            }
            return InteractionLuaBinding.wrapValue(engine, Platform.getInteraction().getGameRule(rule));
        });

        WorldEnvSupport.bindPair(table, "SetGameRule", args ->
                engine.wrapBoolean(handleSetGameRule(args)));
    }

    private static boolean handleSetWorldBorder(ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        if (!InteractionBindingSupport.hasMinArgs(args, off, MIN_BORDER_NUMBERS)) {
            return false;
        }
        boolean hasDim = args[off].isString();
        String dim = hasDim ? args[off].asString().trim() : InteractionBindingSupport.DEFAULT_DIMENSION;
        int idx = hasDim ? off + 1 : off;
        if (dim.isEmpty() || !InteractionBindingSupport.hasMinArgs(args, idx, MIN_BORDER_NUMBERS)) {
            return false;
        }
        return applyBorderNumbers(dim, args, idx);
    }

    private static boolean applyBorderNumbers(String dim, ILuaValue[] args, int idx) {
        ILuaValue cx = args[idx];
        ILuaValue cz = args[idx + 1];
        ILuaValue size = args[idx + 2];
        Long lerpMillis = parseBorderLerp(args, idx + MIN_BORDER_NUMBERS);
        if (lerpMillis == null
                || !WorldEnvSupport.isFiniteNumber(cx)
                || !WorldEnvSupport.isFiniteNumber(cz)
                || !WorldEnvSupport.isFiniteNumber(size)) {
            return false;
        }
        return Platform.getInteraction().setWorldBorder(
                dim, cx.asDouble(), cz.asDouble(), size.asDouble(), lerpMillis);
    }

    @Nullable
    private static Long parseBorderLerp(ILuaValue[] args, int lerpIdx) {
        if (lerpIdx >= args.length || args[lerpIdx].isNil()) {
            return 0L;
        }
        if (!WorldEnvSupport.isFiniteNumber(args[lerpIdx]) || args[lerpIdx].asDouble() < MIN_LERP_MILLIS) {
            return null;
        }
        return (long) Math.floor(args[lerpIdx].asDouble());
    }

    private static boolean handleSetGameRule(ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        if (!InteractionBindingSupport.hasMinArgs(args, off, GAMERULE_SET_MIN_ARGS) || !args[off].isString()) {
            return false;
        }
        String rule = args[off].asString().trim();
        Object value = extractGameRuleValue(args[off + 1]);
        if (rule.isEmpty() || value == null) {
            return false;
        }
        return Platform.getInteraction().setGameRule(rule, value);
    }

    @Nullable
    private static Object extractGameRuleValue(ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (WorldEnvSupport.isFiniteNumber(value)) {
            return (int) Math.floor(value.asDouble());
        }
        return null;
    }
}
