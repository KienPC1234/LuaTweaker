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
 * Parses the volumetric noise layers of a terrain spec
 * (terrain heights and the 3D density field).
 */
final class TerrainLayerParser {

    private TerrainLayerParser() {
    }

    static List<TerrainSpec.WeightedLayer> parseWeightedLayers(String dimensionId, ILuaValue value,
            String key, boolean require3D) {
        ILuaTable table = requireLayerArray(value, dimensionId, key, require3D);
        int count = table.length();
        if (count == 0) {
            throw new IllegalArgumentException("terrain spec for '" + dimensionId + "' has an empty '"
                    + key + "' array");
        }
        List<TerrainSpec.WeightedLayer> layers = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            layers.add(parseWeightedLayer(table.rawget(i), key, i, require3D));
        }
        return layers;
    }

    @NotNull
    private static ILuaTable requireLayerArray(@Nullable ILuaValue value,
            @NotNull String dimensionId, @NotNull String key, boolean require3D) {
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException("terrain spec for '" + dimensionId
                    + "' needs a '" + key + "' array of noise layers"
                    + (require3D ? "" : " (use SetChunkTerrain for a Lua callback)"));
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec '" + key + "' for '" + dimensionId + "' must be an array");
        }
        return value.asTable();
    }

    private static TerrainSpec.WeightedLayer parseWeightedLayer(@Nullable ILuaValue entry,
            String key, int index, boolean require3D) {
        String where = key + "[" + index + "]";
        if (entry == null || entry.isNil() || !entry.isTable()) {
            throw new IllegalArgumentException(where + " must be a table {kind=..., amp=..., frequency=...}");
        }
        ILuaTable layerTable = entry.asTable();
        NoiseLayer noise = noiseLayer(layerTable, where);
        validateLayerKind(noise, require3D, where);
        double amplitude = LuaTableValues.number(layerTable, "amp", 1.0, where);
        return new TerrainSpec.WeightedLayer(noise, amplitude);
    }

    private static void validateLayerKind(@NotNull NoiseLayer noise, boolean require3D,
            @NotNull String where) {
        if (require3D && noise.is2D()) {
            throw new IllegalArgumentException(where + " uses 2D kind '" + noise.kind()
                    + "'; density layers must be 3D (" + NoiseLayer.KINDS_3D + ")");
        }
        if (!require3D && !noise.is2D()) {
            throw new IllegalArgumentException(where + " uses 3D kind '" + noise.kind()
                    + "'; height layers must be 2D (" + NoiseLayer.KINDS_2D + ")");
        }
    }

    private static NoiseLayer noiseLayer(ILuaTable table, String where) {
        String kind = LuaTableValues.string(table, "kind", null);
        if (kind == null) {
            throw new IllegalArgumentException(where + ".kind is required (one of " + NoiseLayer.KINDS_2D + ")");
        }
        try {
            return new NoiseLayer(
                    kind.toLowerCase(Locale.ROOT),
                    LuaTableValues.number(table, "frequency", 1.0, where),
                    LuaTableValues.intValue(table, "octaves", 4, where),
                    LuaTableValues.number(table, "lacunarity", 2.0, where),
                    LuaTableValues.number(table, "gain", 0.5, where),
                    LuaTableValues.number(table, "jitter", 0.8, where),
                    LuaTableValues.intValue(table, "returnType", 0, where),
                    LuaTableValues.number(table, "warpStrength", 20.0, where),
                    LuaTableValues.number(table, "warpFrequency", 1.0, where));
        } catch (IllegalArgumentException e) {
            throw LuaTableValues.invalid(where, e);
        }
    }
}
