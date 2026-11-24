package com.luatweaker.common.worldgen;

import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import dev.architectury.hooks.level.biome.BiomeProperties;
import dev.architectury.platform.Platform;
import dev.architectury.registry.level.biome.BiomeModifications;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges Lua-registered worldgen entries to Architectury {@link BiomeModifications}
 * on loaders that do not read {@code neoforge/biome_modifier} JSON files (e.g. Fabric).
 */
public final class ArchitecturyWorldgenApplier {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private static final Map<String, List<String>> VANILLA_ORE_FEATURES = Map.ofEntries(
            Map.entry("minecraft:coal_ore", List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower")),
            Map.entry("minecraft:iron_ore", List.of("minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small")),
            Map.entry("minecraft:gold_ore", List.of("minecraft:ore_gold", "minecraft:ore_gold_lower", "minecraft:ore_gold_extra")),
            Map.entry("minecraft:redstone_ore", List.of("minecraft:ore_redstone", "minecraft:ore_redstone_lower")),
            Map.entry("minecraft:lapis_ore", List.of("minecraft:ore_lapis", "minecraft:ore_lapis_buried")),
            Map.entry("minecraft:diamond_ore", List.of("minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried")),
            Map.entry("minecraft:emerald_ore", List.of("minecraft:ore_emerald")),
            Map.entry("minecraft:copper_ore", List.of("minecraft:ore_copper", "minecraft:ore_copper_large")),
            Map.entry("minecraft:nether_quartz_ore", List.of("minecraft:ore_quartz_nether")),
            Map.entry("minecraft:nether_gold_ore", List.of("minecraft:ore_gold_nether")),
            Map.entry("minecraft:ancient_debris", List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small"))
    );

    private ArchitecturyWorldgenApplier() {
    }

    public static void register() {
        if (!Platform.isFabric() || !REGISTERED.compareAndSet(false, true)) {
            return;
        }
        BiomeModifications.addProperties(ArchitecturyWorldgenApplier::applyBiomeProperties);
    }

    private static void applyBiomeProperties(BiomeModifications.BiomeContext context,
                                             BiomeProperties.Mutable mutable) {
        Object raw = LuaServiceRegistry.get("WorldgenServiceImpl");
        if (!(raw instanceof WorldgenServiceImpl service)) {
            return;
        }
        Optional<ResourceLocation> biomeKey = context.getKey();
        String biomeId = biomeKey.map(ResourceLocation::toString).orElse("");

        applyOreEntries(context, mutable, service.getPendingOres(), biomeId);
        applyVegetationEntries(mutable, service.getPendingVegetation(), biomeId);
        applyOreRemovals(context, mutable, service.getPendingRemovals());
    }

    private static void applyOreEntries(BiomeModifications.BiomeContext context,
                                        BiomeProperties.Mutable mutable,
                                        List<WorldgenServiceImpl.OreEntry> ores,
                                        String biomeId) {
        for (int i = 0; i < ores.size(); i++) {
            WorldgenServiceImpl.OreEntry ore = ores.get(i);
            if (!matchesDimensionAndBiomes(context, biomeId, ore.dimension(), ore.biomes())) {
                continue;
            }
            String featureId = ore.blockId().replace(":", "_") + "_ore_" + i;
            ResourceKey<PlacedFeature> key = ResourceKey.create(
                    Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath("luatweaker", featureId));
            mutable.getGenerationProperties().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, key);
        }
    }

    private static void applyVegetationEntries(BiomeProperties.Mutable mutable,
                                               List<WorldgenServiceImpl.VegetationEntry> entries,
                                               String biomeId) {
        for (int i = 0; i < entries.size(); i++) {
            WorldgenServiceImpl.VegetationEntry veg = entries.get(i);
            String baseFeatureId = veg.blockId().replace(":", "_") + "_veg_" + i;
            for (int j = 0; j < veg.biomes().size(); j++) {
                if (biomeId.equals(veg.biomes().get(j))) {
                    ResourceKey<PlacedFeature> key = ResourceKey.create(
                            Registries.PLACED_FEATURE,
                            ResourceLocation.fromNamespaceAndPath("luatweaker", baseFeatureId + "_" + j));
                    mutable.getGenerationProperties().addFeature(
                            GenerationStep.Decoration.VEGETAL_DECORATION, key);
                }
            }
        }
    }

    private static void applyOreRemovals(BiomeModifications.BiomeContext context,
                                         BiomeProperties.Mutable mutable,
                                         List<WorldgenServiceImpl.OreRemovalEntry> removals) {
        for (WorldgenServiceImpl.OreRemovalEntry removal : removals) {
            if (!matchesDimensionTag(context, removal.dimension())) {
                continue;
            }
            List<String> features = VANILLA_ORE_FEATURES.get(removal.blockId());
            if (features == null) {
                continue;
            }
            for (String featureId : features) {
                ResourceKey<PlacedFeature> key = ResourceKey.create(
                        Registries.PLACED_FEATURE, ResourceLocation.parse(featureId));
                mutable.getGenerationProperties().removeFeature(
                        GenerationStep.Decoration.UNDERGROUND_ORES, key);
            }
        }
    }

    private static boolean matchesDimensionAndBiomes(BiomeModifications.BiomeContext context,
                                                     String biomeId,
                                                     String dimension,
                                                     List<String> biomes) {
        if (!biomes.isEmpty()) {
            return biomes.contains(biomeId);
        }
        return matchesDimensionTag(context, dimension);
    }

    private static boolean matchesDimensionTag(BiomeModifications.BiomeContext context, String dimension) {
        TagKey<Biome> tag = switch (dimension) {
            case "minecraft:overworld" -> BiomeTags.IS_OVERWORLD;
            case "minecraft:the_nether" -> BiomeTags.IS_NETHER;
            case "minecraft:the_end" -> BiomeTags.IS_END;
            default -> null;
        };
        return tag != null && context.hasTag(tag);
    }
}
