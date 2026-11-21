package com.luatweaker.interaction;

import com.luatweaker.api.particle.ParticleOptionsSpec;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Reads the {@code Particle:SpawnOptions} options table. */
final class ParticleOptionsReader {

    private static final int MAX_RGB = 0xFFFFFF;
    private static final double DEFAULT_DUST_SCALE = 1.0;

    private ParticleOptionsReader() {
    }

    static ParticleOptionsSpec read(ILuaTable table) {
        Integer color = readColor(first(table, "Color", "color"));
        Double scale = readScale(first(table, "Size", "size"));
        String blockId = readId(first(table, "Block", "block"), "Block");
        String itemId = readId(first(table, "Item", "item"), "Item");
        validateCombination(color, scale, blockId, itemId);
        if (color != null && scale == null) {
            scale = DEFAULT_DUST_SCALE;
        }
        return new ParticleOptionsSpec(color, scale, blockId, itemId);
    }

    private static void validateCombination(@Nullable Integer color, @Nullable Double scale,
                                            @Nullable String blockId, @Nullable String itemId) {
        validateExclusive(blockId, itemId);
        validateRequired(color, scale, blockId, itemId);
        if (scale != null && color == null) {
            throw new IllegalArgumentException(
                    "Particle Size requires a Color (dust particles need both)");
        }
    }

    private static void validateExclusive(@Nullable String blockId, @Nullable String itemId) {
        if (blockId != null && itemId != null) {
            throw new IllegalArgumentException(
                    "Particle options cannot request both Block and Item");
        }
    }

    private static void validateRequired(@Nullable Integer color, @Nullable Double scale,
                                         @Nullable String blockId, @Nullable String itemId) {
        boolean dust = color != null || scale != null;
        boolean shaped = blockId != null || itemId != null;
        if (dust && shaped) {
            throw new IllegalArgumentException(
                    "Particle options cannot mix dust Color/Size with Block/Item");
        }
        if (!dust && !shaped) {
            throw new IllegalArgumentException(
                    "Particle options need at least one of Color, Size, Block or Item");
        }
    }

    private static @Nullable Integer readColor(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        int rgb;
        if (value.isNumber()) {
            rgb = value.asInt();
        } else {
            rgb = parseHex(value.asString());
        }
        if (rgb < 0 || rgb > MAX_RGB) {
            throw new IllegalArgumentException(
                    "Particle Color must be between 0 and " + MAX_RGB);
        }
        return rgb;
    }

    private static int parseHex(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        if (!text.matches("[0-9a-f]{6}")) {
            throw new IllegalArgumentException(
                    "Particle Color must be '#RRGGBB' or a 24-bit integer (got '" + raw + "')");
        }
        return Integer.parseInt(text, 16);
    }

    private static @Nullable Double readScale(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        double scale = value.asDouble();
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("Particle Size must be a finite number > 0");
        }
        return scale;
    }

    private static @Nullable String readId(@Nullable ILuaValue value, String label) {
        if (value == null || value.isNil()) {
            return null;
        }
        String id = value.asString();
        if (id.isBlank()) {
            throw new IllegalArgumentException("Particle " + label + " id must not be blank");
        }
        return id;
    }

    private static @Nullable ILuaValue first(ILuaTable table, String upper, String lower) {
        ILuaValue value = table.rawget(upper);
        if (value == null || value.isNil()) {
            value = table.rawget(lower);
        }
        return value;
    }
}
