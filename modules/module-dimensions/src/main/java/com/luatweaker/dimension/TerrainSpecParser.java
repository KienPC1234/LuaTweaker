package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.noise.NoiseLayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the table passed to {@code Dimensions:SetTerrainSpec} into an immutable
 * {@link TerrainSpec}. Every error names the offending field so a broken script fails at
 * registration time instead of during chunk generation.
 */
public final class TerrainSpecParser {

    private TerrainSpecParser() {}

    @NotNull
    public static TerrainSpec parse(@NotNull String dimensionId, @NotNull ILuaValue value,
                                    @NotNull DimensionConfig config) {
        if (value == null || value.isNil() || !value.isTable()) {
            throw new IllegalArgumentException("terrain spec for '" + dimensionId + "' must be a table");
        }
        ILuaTable table = value.asTable();

        Integer seaLevel = LuaTableValues.optionalInt(table, "seaLevel");
        if (seaLevel != null && (seaLevel <= config.minHeight() || seaLevel >= config.maxHeight())) {
            throw new IllegalArgumentException("terrain spec seaLevel (" + seaLevel + ") for '" + dimensionId
                    + "' must be between minHeight (" + config.minHeight() + ") and maxHeight ("
                    + config.maxHeight() + ")");
        }

        List<TerrainSpec.WeightedLayer> layers;
        ILuaValue heightValue = table.rawget("height");
        TerrainSpec.Density density = parseDensity(dimensionId, table.rawget("density"));
        if (density != null && (heightValue == null || heightValue.isNil())) {
            layers = List.of();
        } else {
            layers = parseWeightedLayers(dimensionId, heightValue, "height", false);
        }
        List<TerrainSpec.SplinePoint> spline = parseSpline(dimensionId, table.rawget("spline"), "spline");
        List<TerrainSpec.SurfaceRule> rules =
                parseSurfaceRules(dimensionId, table.rawget("surface"), config);
        List<TerrainSpec.Platform> platforms = parsePlatforms(dimensionId, table.rawget("platforms"));
        boolean water = LuaTableValues.bool(table, "water", true);
        return new TerrainSpec(seaLevel, layers, spline, rules, platforms, density, water);
    }

    private static List<TerrainSpec.WeightedLayer> parseWeightedLayers(String dimensionId, ILuaValue value,
                                                                       String key, boolean require3D) {
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException("terrain spec for '" + dimensionId
                    + "' needs a '" + key + "' array of noise layers"
                    + (require3D ? "" : " (use SetChunkTerrain for a Lua callback)"));
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec '" + key + "' for '" + dimensionId + "' must be an array");
        }
        ILuaTable table = value.asTable();
        int count = table.length();
        if (count == 0) {
            throw new IllegalArgumentException("terrain spec for '" + dimensionId + "' has an empty '"
                    + key + "' array");
        }
        List<TerrainSpec.WeightedLayer> layers = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ILuaValue entry = table.rawget(i);
            String where = key + "[" + i + "]";
            if (entry == null || entry.isNil() || !entry.isTable()) {
                throw new IllegalArgumentException(where + " must be a table {kind=..., amp=..., frequency=...}");
            }
            ILuaTable layerTable = entry.asTable();
            NoiseLayer noise = noiseLayer(layerTable, where);
            if (require3D && noise.is2D()) {
                throw new IllegalArgumentException(where + " uses 2D kind '" + noise.kind()
                        + "'; density layers must be 3D (" + NoiseLayer.KINDS_3D + ")");
            }
            if (!require3D && !noise.is2D()) {
                throw new IllegalArgumentException(where + " uses 3D kind '" + noise.kind()
                        + "'; height layers must be 2D (" + NoiseLayer.KINDS_2D + ")");
            }
            double amplitude = number(layerTable, "amp", 1.0, where);
            layers.add(new TerrainSpec.WeightedLayer(noise, amplitude));
        }
        return layers;
    }

    @Nullable
    private static TerrainSpec.Density parseDensity(String dimensionId, ILuaValue value) {
        if (value == null || value.isNil()) return null;
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec 'density' for '" + dimensionId + "' must be a table");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.WeightedLayer> layers =
                parseWeightedLayers(dimensionId, table.rawget("layers"), "density.layers", true);
        List<TerrainSpec.SplinePoint> thresholdSpline =
                parseSpline(dimensionId, table.rawget("thresholdSpline"), "density.thresholdSpline");
        try {
            return new TerrainSpec.Density(
                    layers,
                    LuaTableValues.number(table, "threshold", 0.0),
                    (int) Math.floor(LuaTableValues.number(table, "cellSize", DensityField.DEFAULT_CELL_SIZE)),
                    (int) Math.floor(LuaTableValues.number(table, "cellHeight", DensityField.DEFAULT_CELL_HEIGHT)),
                    thresholdSpline);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("terrain spec 'density' for '" + dimensionId
                    + "' is invalid: " + e.getMessage(), e);
        }
    }

    private static List<TerrainSpec.Platform> parsePlatforms(String dimensionId, ILuaValue value) {
        if (value == null || value.isNil()) return List.of();
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec 'density.platforms' for '" + dimensionId
                    + "' must be an array of {x, z, radius, bottomY, topY}");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.Platform> platforms = new ArrayList<>(table.length());
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue entry = table.rawget(i);
            String where = "density.platforms[" + i + "]";
            if (entry == null || entry.isNil() || !entry.isTable()) {
                throw new IllegalArgumentException(where + " must be a table {x, z, radius, bottomY, topY}");
            }
            ILuaTable platform = entry.asTable();
            try {
                platforms.add(new TerrainSpec.Platform(
                        requiredInt(platform, "x", where),
                        requiredInt(platform, "z", where),
                        requiredInt(platform, "radius", where),
                        requiredInt(platform, "bottomY", where),
                        requiredInt(platform, "topY", where)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + " is invalid: " + e.getMessage(), e);
            }
        }
        return platforms;
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

    private static NoiseLayer noiseLayer(ILuaTable table, String where) {
        String kind = LuaTableValues.string(table, "kind", null);
        if (kind == null) {
            throw new IllegalArgumentException(where + ".kind is required (one of " + NoiseLayer.KINDS_2D + ")");
        }
        try {
            return new NoiseLayer(
                    kind.toLowerCase(Locale.ROOT),
                    number(table, "frequency", 1.0, where),
                    intValue(table, "octaves", 4, where),
                    number(table, "lacunarity", 2.0, where),
                    number(table, "gain", 0.5, where),
                    number(table, "jitter", 0.8, where),
                    intValue(table, "returnType", 0, where),
                    number(table, "warpStrength", 20.0, where),
                    number(table, "warpFrequency", 1.0, where));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + " is invalid: " + e.getMessage(), e);
        }
    }

    private static List<TerrainSpec.SplinePoint> parseSpline(String dimensionId, ILuaValue value, String key) {
        if (value == null || value.isNil()) return List.of();
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec '" + key + "' for '" + dimensionId
                    + "' must be an array of {input, output} points");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.SplinePoint> points = new ArrayList<>(table.length());
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue entry = table.rawget(i);
            String where = key + "[" + i + "]";
            if (entry == null || entry.isNil() || !entry.isTable()) {
                throw new IllegalArgumentException(where + " must be a table {input, output}");
            }
            ILuaTable point = entry.asTable();
            ILuaValue inputValue = point.rawget("input");
            ILuaValue outputValue = point.rawget("output");
            if (inputValue == null || inputValue.isNil()) inputValue = point.rawget(1);
            if (outputValue == null || outputValue.isNil()) outputValue = point.rawget(2);
            if (inputValue == null || !inputValue.isNumber() || outputValue == null || !outputValue.isNumber()) {
                throw new IllegalArgumentException(where + " must provide numeric input and output");
            }
            try {
                points.add(new TerrainSpec.SplinePoint(inputValue.asDouble(), outputValue.asDouble()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + " is invalid: " + e.getMessage(), e);
            }
        }
        return points;
    }

    private static List<TerrainSpec.SurfaceRule> parseSurfaceRules(String dimensionId, ILuaValue value,
                                                                    DimensionConfig config) {
        if (value == null || value.isNil()) return List.of();
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec 'surface' for '" + dimensionId
                    + "' must be an array of rules {block, maxDepth, minY, maxY, minSlope}");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.SurfaceRule> rules = new ArrayList<>(table.length());
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue entry = table.rawget(i);
            String where = "surface[" + i + "]";
            if (entry == null || entry.isNil() || !entry.isTable()) {
                throw new IllegalArgumentException(where + " must be a table {block=..., maxDepth=...}");
            }
            ILuaTable ruleTable = entry.asTable();
            String block = LuaTableValues.string(ruleTable, "block", null);
            if (block == null) {
                throw new IllegalArgumentException(where + ".block is required");
            }
            if (!LuaTableValues.isResourceLocation(block)) {
                throw new IllegalArgumentException(where + ".block must be a valid block id, got: " + block);
            }
            try {
                rules.add(new TerrainSpec.SurfaceRule(
                        block,
                        intValue(ruleTable, "maxDepth", 1, where),
                        intValue(ruleTable, "minY", config.minHeight(), where),
                        intValue(ruleTable, "maxY", config.maxHeight() - 1, where),
                        number(ruleTable, "minSlope", 0.0, where)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + " is invalid: " + e.getMessage(), e);
            }
        }
        return rules;
    }

    private static double number(ILuaTable table, String key, double fallback, String where) {
        try {
            return LuaTableValues.number(table, key, fallback);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + "." + key + " is invalid: " + e.getMessage(), e);
        }
    }

    private static int intValue(ILuaTable table, String key, int fallback, String where) {
        return (int) Math.floor(number(table, key, fallback, where));
    }
}
