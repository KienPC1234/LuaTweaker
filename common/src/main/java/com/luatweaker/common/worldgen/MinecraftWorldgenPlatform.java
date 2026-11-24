package com.luatweaker.common.worldgen;

import com.luatweaker.api.pal.IPlatformWorldgen;
import com.luatweaker.common.CommonServerHolder;
import dev.architectury.hooks.level.biome.BiomeHooks;
import dev.architectury.hooks.level.biome.ClimateProperties;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads live worldgen data from the running server's biome registry using
 * Architectury {@link BiomeHooks} so no internal field access widener is needed.
 */
public final class MinecraftWorldgenPlatform implements IPlatformWorldgen {

    @Override
    public @Nullable Map<String, Object> getBiomeInfo(@NotNull String biomeId) {
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        return id == null ? null : biomeInfo(CommonServerHolder.get(), id);
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
        ClimateProperties climate = BiomeHooks.getBiomeProperties(biome).getClimateProperties();
        info.put("id", id.toString());
        info.put("hasPrecipitation", climate.hasPrecipitation());
        info.put("temperature", (double) climate.getTemperature());
        info.put("downfall", (double) climate.getDownfall());
        info.put("temperatureModifier", climate.getTemperatureModifier().getSerializedName());
        var effects = biome.getSpecialEffects();
        info.put("skyColor", biome.getSkyColor());
        info.put("fogColor", biome.getFogColor());
        info.put("waterColor", biome.getWaterColor());
        info.put("waterFogColor", biome.getWaterFogColor());
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
