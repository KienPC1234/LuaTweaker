package com.luatweaker.platform.worldgen;

import com.luatweaker.api.pal.IPlatformWorldgen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads live worldgen data from the running server's registries.
 *
 * <p>Biome values come from {@link Biome#getModifiedClimateSettings()} and
 * {@link Biome#getModifiedSpecialEffects()}, so datapack/biome-modifier changes are reflected
 * instead of the raw JSON defaults.</p>
 */
public final class NeoForgeWorldgenPlatform implements IPlatformWorldgen {

    @Override
    public @Nullable Map<String, Object> getBiomeInfo(@NotNull String biomeId) {
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        return id == null ? null : biomeInfo(ServerLifecycleHooks.getCurrentServer(), id);
    }

    @Nullable
    private static Map<String, Object> biomeInfo(@Nullable MinecraftServer server, ResourceLocation id) {
        if (server == null) {
            return null;
        }
        Optional<Registry<Biome>> registry = server.registryAccess().registry(Registries.BIOME);
        if (registry.isEmpty()) {
            return null;
        }
        Biome biome = registry.get().get(id);
        return biome == null ? null : describe(biome, id);
    }

    @NotNull
    private static Map<String, Object> describe(Biome biome, ResourceLocation id) {
        var info = new LinkedHashMap<String, Object>();
        info.put("id", id.toString());
        var climate = biome.getModifiedClimateSettings();
        info.put("hasPrecipitation", climate.hasPrecipitation());
        info.put("temperature", (double) climate.temperature());
        info.put("downfall", (double) climate.downfall());
        info.put("temperatureModifier", climate.temperatureModifier().getSerializedName());
        var effects = biome.getModifiedSpecialEffects();
        info.put("skyColor", effects.getSkyColor());
        info.put("fogColor", effects.getFogColor());
        info.put("waterColor", effects.getWaterColor());
        info.put("waterFogColor", effects.getWaterFogColor());
        effects.getGrassColorOverride().ifPresent(color -> info.put("grassColor", color));
        effects.getFoliageColorOverride().ifPresent(color -> info.put("foliageColor", color));
        info.put("spawners", spawners(biome));
        return info;
    }

    @NotNull
    private static Map<String, Object> spawners(Biome biome) {
        var spawners = new LinkedHashMap<String, Object>();
        for (MobCategory category : MobCategory.values()) {
            List<MobSpawnSettings.SpawnerData> entries = biome.getMobSettings().getMobs(category).unwrap();
            if (entries.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> described = describeSpawners(entries);
            if (!described.isEmpty()) {
                spawners.put(category.getSerializedName(), described);
            }
        }
        return spawners;
    }

    @NotNull
    private static List<Map<String, Object>> describeSpawners(List<MobSpawnSettings.SpawnerData> entries) {
        List<Map<String, Object>> described = new ArrayList<>(entries.size());
        for (MobSpawnSettings.SpawnerData entry : entries) {
            Map<String, Object> spawn = describeSpawner(entry);
            if (spawn != null) {
                described.add(spawn);
            }
        }
        return described;
    }

    @Nullable
    private static Map<String, Object> describeSpawner(MobSpawnSettings.SpawnerData entry) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.type);
        if (entityId == null) {
            return null;
        }
        var spawn = new LinkedHashMap<String, Object>();
        spawn.put("entity", entityId.toString());
        spawn.put("weight", entry.getWeight().asInt());
        spawn.put("minGroup", entry.minCount);
        spawn.put("maxGroup", entry.maxCount);
        return spawn;
    }
}
