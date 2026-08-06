package com.luatweaker.platform.dimension;

import com.google.gson.JsonObject;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge side of the dimension system:
 * <ul>
 *   <li>Registers the {@code luatweaker:lua} chunk generator + biome source
 *       codecs so dimension JSONs can reference them.</li>
 *   <li>Materializes every Lua-registered dimension into virtual datapack
 *       files (dimension type + dimension entries) via the DatapackService.</li>
 * </ul>
 */
public final class NeoForgeDimensionProvider {

    public static final String GENERATOR_TYPE = "luatweaker:lua";

    private NeoForgeDimensionProvider() {}

    /** Called from the mod event bus; registers the custom generator codecs. */
    public static void registerCodecs(RegisterEvent event) {
        event.register(Registries.CHUNK_GENERATOR,
                ResourceLocation.parse(GENERATOR_TYPE), () -> LuaChunkGenerator.MAP_CODEC);
        event.register(Registries.BIOME_SOURCE,
                ResourceLocation.parse(GENERATOR_TYPE), () -> LuaBiomeSource.MAP_CODEC);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered Lua chunk generator + biome source codecs under '" + GENERATOR_TYPE + "'");
    }

    /** Writes dimension type + dimension JSONs for every registered dimension. */
    public static void applyAll(@NotNull IDatapackService datapackService) {
        Object serviceObj = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        if (!(serviceObj instanceof DimensionServiceImpl service)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "DimensionService not found; skipping dimension datapack materialization");
            return;
        }
        for (String dimensionId : service.getDimensionIds()) {
            DimensionConfig config = service.getConfig(dimensionId);
            if (config == null) continue;
            String[] parts = splitId(dimensionId);
            String typeJson = buildDimensionTypeJson(config);
            String dimensionJson = buildDimensionJson(config);
            datapackService.addData("data/" + parts[0] + "/dimension_type/" + parts[1] + ".json", typeJson);
            datapackService.addData("data/" + parts[0] + "/dimension/" + parts[1] + ".json", dimensionJson);
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Materialized dimension '" + dimensionId + "' into the virtual datapack");
        }
    }

    /** Splits "namespace:path" -> [namespace, path]; defaults the namespace to "luatweaker". */
    private static String[] splitId(String id) {
        int colon = id.indexOf(':');
        if (colon > 0) return new String[]{id.substring(0, colon), id.substring(colon + 1)};
        return new String[]{"luatweaker", id};
    }

    private static String buildDimensionTypeJson(DimensionConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("ultrawarm", config.ultraWarm());
        json.addProperty("natural", config.natural());
        json.addProperty("piglin_safe", config.piglinSafe());
        json.addProperty("respawn_anchor_works", config.respawnAnchorWorks());
        json.addProperty("bed_works", config.bedWorks());
        json.addProperty("has_raids", config.hasRaids());
        json.addProperty("has_skylight", config.hasSkyLight());
        json.addProperty("has_ceiling", config.hasCeiling());
        json.addProperty("coordinate_scale", config.coordinateScale());
        json.addProperty("ambient_light", config.ambientLight());
        json.addProperty("logical_height", config.logicalHeight());
        // Custom sky effects are registered client-side under "luatweaker:lua";
        // on clients without the registration the manager falls back to the
        // default overworld effects.
        json.addProperty("effects", config.effectsLocation());
        json.addProperty("infiniburn", config.infiniburn());
        json.addProperty("min_y", config.minHeight());
        json.addProperty("height", config.maxHeight() - config.minHeight());
        // monster_spawn_light_level is a keyed IntProvider in 1.21.1 (vanilla
        // uses {"type":"minecraft:uniform",...}); a bare int fails to decode.
        JsonObject monsterLight = new JsonObject();
        monsterLight.addProperty("type", "minecraft:constant");
        monsterLight.addProperty("value", config.monsterSpawnLightLevel());
        json.add("monster_spawn_light_level", monsterLight);
        json.addProperty("monster_spawn_block_light_limit", config.monsterSpawnBlockLightLimit());
        if (config.fixedTime() != null) {
            json.addProperty("fixed_time", config.fixedTime());
        }
        return json.toString();
    }

    private static String buildDimensionJson(DimensionConfig config) {
        JsonObject biomeSource = new JsonObject();
        biomeSource.addProperty("type", GENERATOR_TYPE);
        biomeSource.addProperty("dimension_id", config.id());

        JsonObject generator = new JsonObject();
        generator.addProperty("type", GENERATOR_TYPE);
        generator.addProperty("dimension_id", config.id());
        generator.add("biome_source", biomeSource);

        JsonObject dimension = new JsonObject();
        // The dimension type id MUST be the custom one (data/<ns>/dimension_type/<path>.json);
        // referencing "minecraft:overworld" would silently use vanilla min_y/height
        // and desync the chunk generator bounds.
        dimension.addProperty("type", config.id());
        dimension.add("generator", generator);
        return dimension.toString();
    }
}
