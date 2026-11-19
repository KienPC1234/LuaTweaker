package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.biome.IBiomesService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Merges entity spawn entries into biome datapack JSONs (the virtual datapack
 * files the dimension provider materializes), so entities spawn inside
 * specific biomes via the vanilla spawner.
 */
public class BiomesServiceImpl implements IBiomesService {

    /** Minecraft resource-location charset. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private static final String[] VALID_CATEGORIES = {
            "monster", "creature", "ambient", "water_creature", "water_ambient",
            "water_underground_creature", "misc"
    };

    private final ILuaEngine engine;

    public BiomesServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void addSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity,
                         int weight, int minCount, int maxCount) {
        validateId("biomeId", biomeId);
        validateId("entity", entity);
        validateCategory(category);
        if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got: " + weight);
        if (minCount < 1 || maxCount < minCount) {
            throw new IllegalArgumentException("invalid count range: minCount=" + minCount + " maxCount=" + maxCount);
        }

        String path = biomePath(biomeId);
        JsonObject json = requireBiomeJson(path, biomeId);
        JsonObject spawners = json.getAsJsonObject("spawners");
        if (spawners == null) {
            spawners = new JsonObject();
            json.add("spawners", spawners);
        }
        JsonArray entries = spawners.getAsJsonArray(category);
        if (entries == null) {
            entries = new JsonArray();
            spawners.add(category, entries);
        }
        for (var entry : entries) {
            JsonObject obj = entry.getAsJsonObject();
            if (entity.equals(obj.get("type").getAsString())) {
                obj.addProperty("weight", weight);
                obj.addProperty("minCount", minCount);
                obj.addProperty("maxCount", maxCount);
                writeBiomeJson(path, json);
                return;
            }
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("type", entity);
        entry.addProperty("weight", weight);
        entry.addProperty("minCount", minCount);
        entry.addProperty("maxCount", maxCount);
        entries.add(entry);
        writeBiomeJson(path, json);
    }

    @Override
    public void removeSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity) {
        validateId("biomeId", biomeId);
        validateId("entity", entity);
        validateCategory(category);

        String path = biomePath(biomeId);
        JsonObject json = requireBiomeJson(path, biomeId);
        JsonObject spawners = json.getAsJsonObject("spawners");
        if (spawners == null) return;
        JsonArray entries = spawners.getAsJsonArray(category);
        if (entries == null) return;
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonObject obj = entries.get(i).getAsJsonObject();
            if (entity.equals(obj.get("type").getAsString())) {
                entries.remove(i);
            }
        }
        writeBiomeJson(path, json);
    }

    @Override
    public void define(@NotNull String biomeId, @NotNull ILuaValue config) {
        validateId("biomeId", biomeId);
        if (config == null || !config.isTable()) {
            throw new IllegalArgumentException("config must be a table for biome '" + biomeId + "'");
        }
        ILuaTable t = config.asTable();
        JsonObject json = baseBiomeJson();
        json.addProperty("has_precipitation", bool(t, "has_precipitation", true));
        json.addProperty("temperature", finite(t, "temperature", 0.6, "temperature"));
        json.addProperty("downfall", range(t, "downfall", 0.4, 0.0, 1.0, "downfall"));
        json.add("effects", parseEffects(t.rawget("effects"), biomeId));
        JsonObject spawners = parseSpawners(t.rawget("spawners"), biomeId);
        if (spawners != null) {
            json.add("spawners", spawners);
        }
        JsonObject costs = parseSpawnCosts(t.rawget("spawn_costs"), biomeId);
        if (costs != null) {
            json.add("spawn_costs", costs);
        }
        JsonObject carvers = parseCarvers(t.rawget("carvers"), biomeId);
        if (carvers != null) {
            json.add("carvers", carvers);
        }
        JsonArray features = parseFeatures(t.rawget("features"), biomeId);
        if (features != null) {
            json.add("features", features);
        }
        writeBiomeJson(biomePath(biomeId), json);
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM,
                "[Biomes] Defined biome '" + biomeId + "'.");
    }

    @Override
    @NotNull
    public Object getSpawns(@NotNull String biomeId) {
        validateId("biomeId", biomeId);
        ILuaTable result = engine.createTable();
        IDatapackService datapack = currentDatapack();
        if (datapack == null) return result;
        String existing = datapack.getVirtualFiles().get(biomePath(biomeId));
        if (existing == null) return result;
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(existing).getAsJsonObject();
            JsonObject spawners = json.getAsJsonObject("spawners");
            if (spawners == null) return result;
            for (Map.Entry<String, com.google.gson.JsonElement> cat : spawners.entrySet()) {
                ILuaTable catTable = engine.createTable();
                JsonArray entries = cat.getValue().getAsJsonArray();
                for (int i = 0; i < entries.size(); i++) {
                    JsonObject entry = entries.get(i).getAsJsonObject();
                    ILuaTable entryTable = engine.createTable();
                    if (entry.has("type")) entryTable.rawset("type", entry.get("type").getAsString());
                    if (entry.has("weight")) entryTable.rawset("weight", entry.get("weight").getAsInt());
                    if (entry.has("minCount")) entryTable.rawset("minCount", entry.get("minCount").getAsInt());
                    if (entry.has("maxCount")) entryTable.rawset("maxCount", entry.get("maxCount").getAsInt());
                    catTable.rawset(i + 1, entryTable);
                }
                result.rawset(cat.getKey(), catTable);
            }
        } catch (Exception e) {
            // Malformed biome JSON: return an empty view rather than crashing.
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Datapack JSON merge
    // -------------------------------------------------------------------------

    /**
     * Reads the biome JSON already present in the virtual datapack. Never
     * auto-creates: writing a minimal file for an unknown id would silently
     * shadow a vanilla (or other-mod) biome with an empty definition.
     * Custom biomes must be created with {@link #define} first.
     */
    private JsonObject requireBiomeJson(String path, String biomeId) {
        IDatapackService datapack = currentDatapack();
        if (datapack != null) {
            String existing = datapack.getVirtualFiles().get(path);
            if (existing != null) {
                try {
                    return com.google.gson.JsonParser.parseString(existing).getAsJsonObject();
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Biome '" + biomeId + "' has malformed JSON in the virtual datapack: " + e.getMessage());
                }
            }
        }
        throw new IllegalArgumentException(
            "Unknown biome '" + biomeId + "': define it first with Biomes:Define(\"" + biomeId + "\", {...}) "
            + "instead of letting a minimal file shadow a vanilla biome.");
    }

    /** Starter template for Define: vanilla plains colors, empty spawners/carvers, one empty list per generation step. */
    private JsonObject baseBiomeJson() {
        JsonObject json = new JsonObject();
        json.addProperty("has_precipitation", true);
        json.addProperty("temperature", 0.6);
        json.addProperty("downfall", 0.4);
        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", 7907327);
        effects.addProperty("fog_color", 12638463);
        effects.addProperty("water_color", 4159204);
        effects.addProperty("water_fog_color", 329011);
        json.add("effects", effects);
        json.add("spawners", new JsonObject());
        json.add("spawn_costs", new JsonObject());
        json.add("carvers", new JsonObject());
        JsonArray features = new JsonArray();
        for (int i = 0; i < com.luatweaker.api.worldgen.GenerationSteps.ORDER.size(); i++) {
            features.add(new JsonArray());
        }
        json.add("features", features);
        return json;
    }

    private void writeBiomeJson(String path, JsonObject json) {
        IDatapackService datapack = currentDatapack();
        if (datapack == null) {
            throw new IllegalStateException("Datapack service is not registered; cannot customize biome '" + path + "'");
        }
        datapack.addData(path, json.toString());
    }

    @Nullable
    private IDatapackService currentDatapack() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("Datapack");
        return service instanceof IDatapackService datapack ? datapack : null;
    }

    private static String biomePath(String biomeId) {
        int colon = biomeId.indexOf(':');
        String ns = colon > 0 ? biomeId.substring(0, colon) : "luatweaker";
        String path = colon > 0 ? biomeId.substring(colon + 1) : biomeId;
        // Biomes are a WORLDGEN registry: datapack path is data/<ns>/worldgen/biome/<name>.json.
        return "data/" + ns + "/worldgen/biome/" + path + ".json";
    }

    private static void validateId(String name, String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }

    private static void validateCategory(String category) {
        for (String valid : VALID_CATEGORIES) {
            if (valid.equals(category)) return;
        }
        throw new IllegalArgumentException("invalid spawn category '" + category
                + "'; valid: monster, creature, ambient, water_creature, water_ambient, water_underground_creature, misc");
    }

    // -------------------------------------------------------------------------
    // Define config parsing (field shapes mirror the vanilla 1.21.1 biome codec)
    // -------------------------------------------------------------------------

    private JsonObject parseEffects(@Nullable ILuaValue value, @NotNull String biomeId) {
        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", 7907327);
        effects.addProperty("fog_color", 12638463);
        effects.addProperty("water_color", 4159204);
        effects.addProperty("water_fog_color", 329011);
        if (value == null || value.isNil()) {
            return effects;
        }
        ILuaTable t = requireTable(value, "effects");
        optColor(t, "sky_color", effects);
        optColor(t, "fog_color", effects);
        optColor(t, "water_color", effects);
        optColor(t, "water_fog_color", effects);
        optColor(t, "foliage_color", effects);
        optColor(t, "grass_color", effects);
        ILuaValue modifier = t.rawget("grass_color_modifier");
        if (modifier != null && !modifier.isNil()) {
            String name = modifier.asString();
            if (!"none".equals(name) && !"dark_forest".equals(name) && !"swamp".equals(name)) {
                throw new IllegalArgumentException("effects.grass_color_modifier must be one of"
                        + " none|dark_forest|swamp, got: " + name);
            }
            effects.addProperty("grass_color_modifier", name);
        }
        ILuaValue music = t.rawget("music");
        if (music != null && !music.isNil()) {
            ILuaTable m = requireTable(music, "effects.music");
            JsonObject musicJson = new JsonObject();
            musicJson.addProperty("sound", reqString(m, "sound", "effects.music.sound"));
            musicJson.addProperty("min_delay", (int) reqNumber(m, "min_delay", "effects.music.min_delay"));
            musicJson.addProperty("max_delay", (int) reqNumber(m, "max_delay", "effects.music.max_delay"));
            musicJson.addProperty("replace_current_music", bool(m, "replace_current_music", false));
            effects.add("music", musicJson);
        }
        ILuaValue particle = t.rawget("particle");
        if (particle != null && !particle.isNil()) {
            ILuaTable p = requireTable(particle, "effects.particle");
            String type = reqString(p, "type", "effects.particle.type");
            if (!RESOURCE_LOCATION.matcher(type).matches()) {
                throw new IllegalArgumentException("effects.particle.type must be a valid resource location, got: " + type);
            }
            double probability = reqNumber(p, "probability", "effects.particle.probability");
            if (probability < 0.0 || probability > 1.0) {
                throw new IllegalArgumentException("effects.particle.probability must be between 0.0 and 1.0, got: " + probability);
            }
            JsonObject options = new JsonObject();
            options.addProperty("type", type);
            JsonObject particleJson = new JsonObject();
            particleJson.add("options", options);
            particleJson.addProperty("probability", probability);
            effects.add("particle", particleJson);
        }
        return effects;
    }

    @Nullable
    private JsonObject parseSpawners(@Nullable ILuaValue value, @NotNull String biomeId) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = requireTable(value, "spawners");
        JsonObject spawners = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            ILuaValue key = entry.getKey();
            if (key == null || !key.isString()) {
                throw new IllegalArgumentException("spawners keys must be category strings");
            }
            validateCategory(key.asString());
            ILuaValue listVal = entry.getValue();
            if (listVal == null || !listVal.isTable()) {
                throw new IllegalArgumentException("spawners['" + key.asString() + "'] must be a list of entries");
            }
            JsonArray entries = new JsonArray();
            ILuaTable list = listVal.asTable();
            for (int i = 1; i <= list.length(); i++) {
                ILuaValue item = list.rawget(i);
                if (item == null || !item.isTable()) {
                    throw new IllegalArgumentException("spawners['" + key.asString() + "'][" + i + "] must be a table");
                }
                entries.add(parseSpawnEntry(item.asTable(), "spawners['" + key.asString() + "'][" + i + "]"));
            }
            spawners.add(key.asString(), entries);
        }
        return spawners;
    }

    private JsonObject parseSpawnEntry(@NotNull ILuaTable t, @NotNull String what) {
        String type = reqString(t, "type", what + ".type");
        validateId("entity", type);
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type);
        entry.addProperty("weight", (int) reqNumber(t, "weight", what + ".weight"));
        entry.addProperty("minCount", (int) reqNumber(t, "minCount", what + ".minCount"));
        entry.addProperty("maxCount", (int) reqNumber(t, "maxCount", what + ".maxCount"));
        if (entry.get("minCount").getAsInt() < 1 || entry.get("maxCount").getAsInt() < entry.get("minCount").getAsInt()) {
            throw new IllegalArgumentException(what + " needs 1 <= minCount <= maxCount");
        }
        if (entry.get("weight").getAsInt() < 1) {
            throw new IllegalArgumentException(what + ".weight must be >= 1");
        }
        return entry;
    }

    @Nullable
    private JsonObject parseSpawnCosts(@Nullable ILuaValue value, @NotNull String biomeId) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = requireTable(value, "spawn_costs");
        JsonObject costs = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            ILuaValue key = entry.getKey();
            if (key == null || !key.isString()) {
                throw new IllegalArgumentException("spawn_costs keys must be entity ids");
            }
            validateId("entity", key.asString());
            ILuaValue costVal = entry.getValue();
            if (costVal == null || !costVal.isTable()) {
                throw new IllegalArgumentException("spawn_costs['" + key.asString() + "'] must be a table {charge, energy_budget}");
            }
            ILuaTable cost = costVal.asTable();
            double charge = reqNumber(cost, "charge", "spawn_costs['" + key.asString() + "'].charge");
            double budget = reqNumber(cost, "energy_budget", "spawn_costs['" + key.asString() + "'].energy_budget");
            if (!Double.isFinite(charge) || charge < 0.0 || !Double.isFinite(budget) || budget < 0.0) {
                throw new IllegalArgumentException("spawn_costs['" + key.asString() + "'] values must be finite and >= 0");
            }
            JsonObject costJson = new JsonObject();
            costJson.addProperty("charge", charge);
            costJson.addProperty("energy_budget", budget);
            costs.add(key.asString(), costJson);
        }
        return costs;
    }

    @Nullable
    private JsonObject parseCarvers(@Nullable ILuaValue value, @NotNull String biomeId) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = requireTable(value, "carvers");
        JsonObject carvers = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            ILuaValue key = entry.getKey();
            if (key == null || !key.isString()
                    || (!"air".equals(key.asString()) && !"liquid".equals(key.asString()))) {
                throw new IllegalArgumentException("carvers keys must be 'air' or 'liquid'");
            }
            carvers.add(key.asString(), featureIdList(entry.getValue(), "carvers." + key.asString()));
        }
        return carvers;
    }

    @Nullable
    private JsonArray parseFeatures(@Nullable ILuaValue value, @NotNull String biomeId) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = requireTable(value, "features");
        Map<String, JsonArray> byStep = new java.util.LinkedHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            ILuaValue key = entry.getKey();
            if (key == null || !key.isString()
                    || !com.luatweaker.api.worldgen.GenerationSteps.isKnownStep(key.asString())) {
                throw new IllegalArgumentException("features keys must be known generation steps "
                        + com.luatweaker.api.worldgen.GenerationSteps.ORDER + ", got: "
                        + (key == null ? "nil" : key.asString()));
            }
            byStep.put(key.asString(), featureIdList(entry.getValue(), "features." + key.asString()));
        }
        JsonArray features = new JsonArray();
        for (String step : com.luatweaker.api.worldgen.GenerationSteps.ORDER) {
            JsonArray slot = byStep.get(step);
            features.add(slot != null ? slot : new JsonArray());
        }
        return features;
    }

    private JsonArray featureIdList(@Nullable ILuaValue value, @NotNull String what) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException(what + " must be a list of resource-location strings");
        }
        ILuaTable table = value.asTable();
        JsonArray out = new JsonArray();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue item = table.rawget(i);
            if (item == null || !item.isString()) {
                throw new IllegalArgumentException(what + "[" + i + "] must be a resource-location string");
            }
            String id = item.asString();
            if (id.startsWith("#")) {
                if (!RESOURCE_LOCATION.matcher(id.substring(1)).matches()) {
                    throw new IllegalArgumentException(what + "[" + i + "] has an invalid tag target: " + id);
                }
            } else if (!RESOURCE_LOCATION.matcher(id).matches()) {
                throw new IllegalArgumentException(what + "[" + i + "] must be a valid resource location, got: " + id);
            }
            out.add(id);
        }
        return out;
    }

    private static ILuaTable requireTable(@Nullable ILuaValue value, @NotNull String what) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException(what + " must be a table");
        }
        return value.asTable();
    }

    private static String reqString(@NotNull ILuaTable t, @NotNull String key, @NotNull String what) {
        ILuaValue v = t.rawget(key);
        if (v == null || !v.isString()) {
            throw new IllegalArgumentException(what + " is required and must be a string");
        }
        return v.asString();
    }

    private static double reqNumber(@NotNull ILuaTable t, @NotNull String key, @NotNull String what) {
        ILuaValue v = t.rawget(key);
        if (v == null || !v.isNumber()) {
            throw new IllegalArgumentException(what + " is required and must be a number");
        }
        return v.asDouble();
    }

    private static boolean bool(@NotNull ILuaTable t, @NotNull String key, boolean def) {
        ILuaValue v = t.rawget(key);
        return v != null && !v.isNil() ? v.asBoolean() : def;
    }

    private static double finite(@NotNull ILuaTable t, @NotNull String key, double def, @NotNull String what) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return def;
        if (!v.isNumber() || !Double.isFinite(v.asDouble())) {
            throw new IllegalArgumentException(what + " must be a finite number");
        }
        return v.asDouble();
    }

    private static double range(@NotNull ILuaTable t, @NotNull String key, double def,
                                double min, double max, @NotNull String what) {
        double value = finite(t, key, def, what);
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " must be between " + min + " and " + max + ", got: " + value);
        }
        return value;
    }

    private static void optColor(@NotNull ILuaTable t, @NotNull String key, @NotNull JsonObject effects) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return;
        if (!v.isNumber()) {
            throw new IllegalArgumentException("effects." + key + " must be an RGB number 0x000000-0xFFFFFF");
        }
        int color = (int) v.asDouble();
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("effects." + key + " must be an RGB number 0x000000-0xFFFFFF, got: " + color);
        }
        effects.addProperty(key, color);
    }
}
