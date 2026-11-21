package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the {@code add}/{@code remove}/{@code removeSteps} lists of
 * {@code ModifyBiome}.
 */
final class WorldgenFeatureListParser {

    private static final String KEY_ADD = "add";
    private static final String KEY_FEATURE = "feature";
    private static final String KEY_STEP = "step";
    private static final String KEY_REMOVE = "remove";
    private static final String KEY_REMOVE_STEPS = "removeSteps";

    private WorldgenFeatureListParser() {}

    static @NotNull List<WorldgenServiceImpl.AddedFeature> parseAddedFeatures(
            @NotNull ILuaTable options, @NotNull String defaultStep) {
        ILuaValue add = options.rawget(KEY_ADD);
        if (add == null || add.isNil()) {
            return List.of();
        }
        if (!add.isTable()) {
            return List.of(parseAddedItem(add, defaultStep, "options.add"));
        }
        ILuaTable table = add.asTable();
        if (table == null) {
            throw new IllegalArgumentException("options.add must be a feature ID string or a list/table");
        }
        if (table.length() == 0) {
            ILuaValue singleFeature = table.rawget(KEY_FEATURE);
            if (singleFeature != null && !singleFeature.isNil()) {
                return List.of(parseSingleAdded(table, defaultStep));
            }
            return List.of();
        }
        return parseAddedList(table, defaultStep);
    }

    private static @NotNull List<WorldgenServiceImpl.AddedFeature> parseAddedList(
            @NotNull ILuaTable table, @NotNull String defaultStep) {
        List<WorldgenServiceImpl.AddedFeature> result = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue item = table.rawget(i);
            if (item == null || item.isNil()) {
                break;
            }
            result.add(parseAddedItem(item, defaultStep, "options.add[" + i + "]"));
        }
        return result;
    }

    private static @NotNull WorldgenServiceImpl.AddedFeature parseAddedItem(
            @NotNull ILuaValue item, @NotNull String defaultStep, @NotNull String what) {
        if (item.isTable()) {
            ILuaTable table = item.asTable();
            if (table == null) {
                throw new IllegalArgumentException(what
                        + " must be a feature ID string or {feature=..., step=...}");
            }
            return parseSingleAdded(table, defaultStep);
        }
        String feature = item.asString();
        WorldgenValidation.validateResourceLocation(feature, what);
        return new WorldgenServiceImpl.AddedFeature(feature, defaultStep);
    }

    private static @NotNull WorldgenServiceImpl.AddedFeature parseSingleAdded(
            @NotNull ILuaTable table, @NotNull String defaultStep) {
        String feature = LuaFieldReaders.reqString(table, KEY_FEATURE, "options.add.feature");
        WorldgenValidation.validateResourceLocation(feature, "options.add.feature");
        return new WorldgenServiceImpl.AddedFeature(feature,
                LuaFieldReaders.optStep(table, KEY_STEP, defaultStep));
    }

    static @NotNull List<String> parseRemovedFeatures(@NotNull ILuaTable options) {
        ILuaValue remove = options.rawget(KEY_REMOVE);
        if (remove == null || remove.isNil()) {
            return List.of();
        }
        List<String> features = LuaStringListReader.asStringList(remove, "options.remove");
        for (String feature : features) {
            WorldgenValidation.validateResourceLocation(feature, "options.remove entry");
        }
        return features;
    }

    static @NotNull List<String> parseRemoveSteps(@NotNull ILuaTable options) {
        ILuaValue steps = options.rawget(KEY_REMOVE_STEPS);
        if (steps == null || steps.isNil()) {
            return List.of(WorldgenDefaults.DEFAULT_STEP);
        }
        List<String> parsed = LuaStringListReader.asStringList(steps, "options.removeSteps");
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("options.removeSteps must not be empty");
        }
        for (String step : parsed) {
            WorldgenValidation.parseStep(step, "options.removeSteps entry");
        }
        return parsed;
    }
}
