package com.luatweaker.noise;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Parses the Lua batch spec table into a validated {@link NoiseLayer}. Every field is
 * optional and falls back to the documented default; a wrong Lua type is rejected loudly.
 */
final class NoiseSpecParser {

    private static final String KEY_KIND = "kind";
    private static final String KEY_OCTAVES = "octaves";
    private static final String KEY_LACUNARITY = "lacunarity";
    private static final String KEY_GAIN = "gain";
    private static final String KEY_FREQUENCY = "frequency";
    private static final String KEY_JITTER = "jitter";
    private static final String KEY_RETURN_TYPE = "returnType";
    private static final String KEY_WARP_STRENGTH = "warpStrength";
    private static final String KEY_WARP_FREQUENCY = "warpFrequency";

    private NoiseSpecParser() {}

    @NotNull
    static NoiseLayer parseSpec(@NotNull ILuaValue value, boolean threeDimensional) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException("spec must be a table, got "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        ILuaTable table = value.asTable();
        String kind = stringField(table, KEY_KIND,
                threeDimensional ? "fbm3" : "fbm").toLowerCase(Locale.ROOT);
        int octaves = intField(table, KEY_OCTAVES, 4);
        double lacunarity = doubleField(table, KEY_LACUNARITY, 2.0);
        double gain = doubleField(table, KEY_GAIN, 0.5);
        double frequency = doubleField(table, KEY_FREQUENCY, 1.0);
        double jitter = doubleField(table, KEY_JITTER, 0.8);
        int voronoiType = intField(table, KEY_RETURN_TYPE, 0);
        double warpStrength = doubleField(table, KEY_WARP_STRENGTH, 20.0);
        double warpFrequency = doubleField(table, KEY_WARP_FREQUENCY, 1.0);
        try {
            return new NoiseLayer(kind, frequency, octaves, lacunarity, gain,
                    jitter, voronoiType, warpStrength, warpFrequency);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid noise spec: " + e.getMessage(), e);
        }
    }

    private static String stringField(ILuaTable table, String key, String fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("spec." + key + " must be a string, got "
                    + value.getClass().getSimpleName());
        }
        return value.asString();
    }

    private static int intField(ILuaTable table, String key, int fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException("spec." + key + " must be a number, got "
                    + value.getClass().getSimpleName());
        }
        return (int) Math.floor(value.asDouble());
    }

    private static double doubleField(ILuaTable table, String key, double fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException("spec." + key + " must be a number, got "
                    + value.getClass().getSimpleName());
        }
        return value.asDouble();
    }
}
