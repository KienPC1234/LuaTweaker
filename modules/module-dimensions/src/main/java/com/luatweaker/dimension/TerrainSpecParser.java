package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parses the table passed to {@code Dimensions:SetTerrainSpec} into an immutable
 * {@link TerrainSpec}. Every error names the offending field so a broken script fails at
 * registration time instead of during chunk generation.
 *
 * <p>Section parsing lives in {@link TerrainLayerParser} (noise layers) and
 * {@link TerrainSurfaceParser} (splines, surface rules, platforms).</p>
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

        Integer seaLevel = DimensionConfigParser.validateSeaLevel(table, dimensionId, config);

        ILuaValue heightValue = table.rawget("height");
        TerrainSpec.Density density = parseDensity(dimensionId, table.rawget("density"));
        List<TerrainSpec.WeightedLayer> layers = resolveHeightLayers(dimensionId, heightValue, density);
        List<TerrainSpec.SplinePoint> spline =
                TerrainSplineParser.parseSpline(dimensionId, table.rawget("spline"), "spline");
        List<TerrainSpec.SurfaceRule> rules =
                TerrainSurfaceParser.parseSurfaceRules(dimensionId, table.rawget("surface"), config);
        List<TerrainSpec.Platform> platforms =
                TerrainSurfaceParser.parsePlatforms(dimensionId, table.rawget("platforms"));
        boolean water = LuaTableValues.booleanOr(table, "water", true);
        return new TerrainSpec(seaLevel, layers, spline, rules, platforms, density, water);
    }

    @NotNull
    private static List<TerrainSpec.WeightedLayer> resolveHeightLayers(@NotNull String dimensionId,
            @Nullable ILuaValue heightValue, @Nullable TerrainSpec.Density density) {
        if (density != null && (heightValue == null || heightValue.isNil())) {
            return List.of();
        }
        return TerrainLayerParser.parseWeightedLayers(dimensionId, heightValue, "height", false);
    }

    @Nullable
    private static TerrainSpec.Density parseDensity(String dimensionId, ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec 'density' for '" + dimensionId + "' must be a table");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.WeightedLayer> layers =
                TerrainLayerParser.parseWeightedLayers(dimensionId, table.rawget("layers"), "density.layers", true);
        List<TerrainSpec.SplinePoint> thresholdSpline = TerrainSplineParser.parseSpline(
                dimensionId, table.rawget("thresholdSpline"), "density.thresholdSpline");
        try {
            return new TerrainSpec.Density(
                    layers,
                    LuaTableValues.number(table, "threshold", 0.0),
                    (int) Math.floor(LuaTableValues.number(table, "cellSize", DensityField.DEFAULT_CELL_SIZE)),
                    (int) Math.floor(LuaTableValues.number(table, "cellHeight", DensityField.DEFAULT_CELL_HEIGHT)),
                    thresholdSpline);
        } catch (IllegalArgumentException e) {
            throw LuaTableValues.invalid("terrain spec 'density' for '" + dimensionId + "'", e);
        }
    }
}
