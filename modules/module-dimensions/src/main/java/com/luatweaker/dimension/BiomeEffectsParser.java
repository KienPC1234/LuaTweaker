package com.luatweaker.dimension;

import com.google.gson.JsonObject;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parses the {@code effects} section of a biome definition
 * (field shapes mirror the vanilla 1.21.1 biome codec).
 */
final class BiomeEffectsParser {

    private BiomeEffectsParser() {
    }

    static JsonObject parseEffects(@Nullable ILuaValue value) {
        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", BiomeJson.DEFAULT_SKY_COLOR);
        effects.addProperty("fog_color", BiomeJson.DEFAULT_FOG_COLOR);
        effects.addProperty("water_color", BiomeJson.DEFAULT_WATER_COLOR);
        effects.addProperty("water_fog_color", BiomeJson.DEFAULT_WATER_FOG_COLOR);
        if (value == null || value.isNil()) {
            return effects;
        }
        ILuaTable t = LuaTableReader.requireTable(value, BiomeJson.KEY_EFFECTS);
        LuaTableReader.optColor(t, "sky_color", effects);
        LuaTableReader.optColor(t, "fog_color", effects);
        LuaTableReader.optColor(t, "water_color", effects);
        LuaTableReader.optColor(t, "water_fog_color", effects);
        LuaTableReader.optColor(t, "foliage_color", effects);
        LuaTableReader.optColor(t, "grass_color", effects);
        parseGrassModifier(t, effects);
        parseMusic(t, effects);
        parseParticle(t, effects);
        return effects;
    }

    private static void parseGrassModifier(@NotNull ILuaTable t, @NotNull JsonObject effects) {
        ILuaValue modifier = t.rawget("grass_color_modifier");
        if (modifier == null || modifier.isNil()) {
            return;
        }
        String name = modifier.asString();
        if (!"none".equals(name) && !"dark_forest".equals(name) && !"swamp".equals(name)) {
            throw new IllegalArgumentException("effects.grass_color_modifier must be one of"
                    + " none|dark_forest|swamp, got: " + name);
        }
        effects.addProperty("grass_color_modifier", name);
    }

    private static void parseMusic(@NotNull ILuaTable t, @NotNull JsonObject effects) {
        ILuaValue music = t.rawget("music");
        if (music == null || music.isNil()) {
            return;
        }
        ILuaTable m = LuaTableReader.requireTable(music, "effects.music");
        JsonObject musicJson = new JsonObject();
        musicJson.addProperty("sound", LuaTableReader.reqString(m, "sound", "effects.music.sound"));
        musicJson.addProperty("min_delay", (int) LuaTableReader.reqNumber(m, "min_delay", "effects.music.min_delay"));
        musicJson.addProperty("max_delay", (int) LuaTableReader.reqNumber(m, "max_delay", "effects.music.max_delay"));
        musicJson.addProperty("replace_current_music", LuaTableReader.bool(m, "replace_current_music", false));
        effects.add("music", musicJson);
    }

    private static void parseParticle(@NotNull ILuaTable t, @NotNull JsonObject effects) {
        ILuaValue particle = t.rawget("particle");
        if (particle == null || particle.isNil()) {
            return;
        }
        ILuaTable p = LuaTableReader.requireTable(particle, "effects.particle");
        String type = LuaTableReader.reqString(p, BiomeJson.KEY_TYPE, "effects.particle.type");
        if (!BiomeJson.RESOURCE_LOCATION.matcher(type).matches()) {
            throw new IllegalArgumentException("effects.particle.type must be a valid resource location, got: " + type);
        }
        double probability = LuaTableReader.reqNumber(p, "probability", "effects.particle.probability");
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("effects.particle.probability must be between 0.0 and 1.0, got: " + probability);
        }
        JsonObject options = new JsonObject();
        options.addProperty(BiomeJson.KEY_TYPE, type);
        JsonObject particleJson = new JsonObject();
        particleJson.add("options", options);
        particleJson.addProperty("probability", probability);
        effects.add("particle", particleJson);
    }
}
