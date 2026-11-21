package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the surface sections of a terrain spec
 * (splines, surface rules, forced-solid platforms).
 */
final class TerrainSurfaceParser {

    private TerrainSurfaceParser() {
    }

    static List<TerrainSpec.SurfaceRule> parseSurfaceRules(String dimensionId, ILuaValue value,
            DimensionConfig config) {
        if (value == null || value.isNil()) {
            return List.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec 'surface' for '" + dimensionId
                    + "' must be an array of rules {block, maxDepth, minY, maxY, minSlope}");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.SurfaceRule> rules = new ArrayList<>(table.length());
        for (int i = 1; i <= table.length(); i++) {
            rules.add(parseSurfaceRule(table.rawget(i), i, config));
        }
        return rules;
    }

    private static TerrainSpec.SurfaceRule parseSurfaceRule(@Nullable ILuaValue entry, int index,
            @NotNull DimensionConfig config) {
        String where = "surface[" + index + "]";
        if (entry == null || entry.isNil() || !entry.isTable()) {
            throw new IllegalArgumentException(where + " must be a table {block=..., maxDepth=...}");
        }
        ILuaTable ruleTable = entry.asTable();
        String block = requireSurfaceBlock(ruleTable, where);
        try {
            return new TerrainSpec.SurfaceRule(
                    block,
                    LuaTableValues.intValue(ruleTable, "maxDepth", 1, where),
                    LuaTableValues.intValue(ruleTable, "minY", config.minHeight(), where),
                    LuaTableValues.intValue(ruleTable, "maxY", config.maxHeight() - 1, where),
                    LuaTableValues.number(ruleTable, "minSlope", 0.0, where));
        } catch (IllegalArgumentException e) {
            throw LuaTableValues.invalid(where, e);
        }
    }

    @NotNull
    private static String requireSurfaceBlock(@NotNull ILuaTable ruleTable, @NotNull String where) {
        String block = LuaTableValues.string(ruleTable, "block", null);
        if (block == null) {
            throw new IllegalArgumentException(where + ".block is required");
        }
        if (!LuaTableValues.isResourceLocation(block)) {
            throw new IllegalArgumentException(where + ".block must be a valid block id, got: " + block);
        }
        return block;
    }

    static List<TerrainSpec.Platform> parsePlatforms(String dimensionId, ILuaValue value) {
        if (value == null || value.isNil()) {
            return List.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec 'density.platforms' for '" + dimensionId
                    + "' must be an array of {x, z, radius, bottomY, topY}");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.Platform> platforms = new ArrayList<>(table.length());
        for (int i = 1; i <= table.length(); i++) {
            platforms.add(parsePlatform(table.rawget(i), i));
        }
        return platforms;
    }

    private static TerrainSpec.Platform parsePlatform(@Nullable ILuaValue entry, int index) {
        String where = "density.platforms[" + index + "]";
        if (entry == null || entry.isNil() || !entry.isTable()) {
            throw new IllegalArgumentException(where + " must be a table {x, z, radius, bottomY, topY}");
        }
        ILuaTable platform = entry.asTable();
        try {
            return new TerrainSpec.Platform(
                    requiredInt(platform, "x", where),
                    requiredInt(platform, "z", where),
                    requiredInt(platform, "radius", where),
                    requiredInt(platform, "bottomY", where),
                    requiredInt(platform, "topY", where));
        } catch (IllegalArgumentException e) {
            throw LuaTableValues.invalid(where, e);
        }
    }

    private static int requiredInt(ILuaTable table, String key, String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(where + "." + key + " is required");
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(where + "." + key + " must be a number, got: "
                    + LuaTableValues.describe(value));
        }
        return (int) Math.floor(value.asDouble());
    }
}
