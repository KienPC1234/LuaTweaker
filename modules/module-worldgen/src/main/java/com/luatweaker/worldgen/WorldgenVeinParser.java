package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the mixed-vein {@code ores} list: either a single {@code {block, weight}}
 * table or a list of them.
 */
final class WorldgenVeinParser {

    private static final String KEY_ORES = "ores";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_WEIGHT = "weight";
    private static final int DEFAULT_WEIGHT = 1;
    private static final int MIN_WEIGHT = 0;

    private WorldgenVeinParser() {}

    static @NotNull List<WorldgenServiceImpl.VeinOre> parseVeinOres(@NotNull ILuaTable spec) {
        ILuaValue value = spec.rawget(KEY_ORES);
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException("spec.ores is required (list of {block=..., weight=...})");
        }
        if (!value.isTable() || value.asTable() == null) {
            throw new IllegalArgumentException("spec.ores must be a list of {block=..., weight=...}");
        }
        ILuaTable table = value.asTable();
        if (table.length() == 0) {
            return parseSingleOreTable(table);
        }
        return parseOreList(table);
    }

    private static @NotNull List<WorldgenServiceImpl.VeinOre> parseSingleOreTable(
            @NotNull ILuaTable table) {
        ILuaValue singleBlock = table.rawget(KEY_BLOCK);
        if (singleBlock != null && !singleBlock.isNil()) {
            return List.of(parseVeinOre(table, "spec.ores"));
        }
        throw new IllegalArgumentException("spec.ores must not be empty");
    }

    private static @NotNull List<WorldgenServiceImpl.VeinOre> parseOreList(
            @NotNull ILuaTable table) {
        List<WorldgenServiceImpl.VeinOre> ores = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue item = table.rawget(i);
            if (item == null || item.isNil() || !item.isTable() || item.asTable() == null) {
                throw new IllegalArgumentException("spec.ores[" + i + "] must be {block=..., weight=...}");
            }
            ores.add(parseVeinOre(item.asTable(), "spec.ores[" + i + "]"));
        }
        return ores;
    }

    private static @NotNull WorldgenServiceImpl.VeinOre parseVeinOre(@NotNull ILuaTable entry,
                                                                     @NotNull String what) {
        String block = LuaFieldReaders.reqString(entry, KEY_BLOCK, what + ".block");
        WorldgenValidation.validateResourceLocation(block, what + ".block");
        int weight = DEFAULT_WEIGHT;
        ILuaValue rawWeight = entry.rawget(KEY_WEIGHT);
        if (rawWeight != null && !rawWeight.isNil()) {
            if (!rawWeight.isNumber()) {
                throw new IllegalArgumentException(what + ".weight must be a number");
            }
            weight = (int) rawWeight.asDouble();
        }
        if (weight <= MIN_WEIGHT) {
            throw new IllegalArgumentException(what + ".weight must be > 0, got: " + weight);
        }
        return new WorldgenServiceImpl.VeinOre(block, weight);
    }
}
