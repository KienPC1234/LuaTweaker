package com.luatweaker.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Datapack file planning: turns the pending registrations into relPath -&gt; JSON entries
 * that the platform provider feeds to {@code IDatapackService#addData}. Paths follow the
 * {@code luatweaker/worldgen} / {@code luatweaker/neoforge} conventions.
 */
abstract class AbstractWorldgenFilePlan extends AbstractWorldgenConfig {

    public @NotNull Map<String, String> buildRawFiles() {
        Map<String, String> files = new ConcurrentHashMap<>();
        for (WorldgenServiceImpl.RawConfigured raw : getPendingRawConfigured()) {
            files.put(WorldgenDefaults.CONFIGURED_FEATURE_DIR
                    + WorldgenJson.sanitizeFileId(raw.featureId()) + WorldgenDefaults.JSON_SUFFIX, raw.json());
        }
        for (WorldgenServiceImpl.RawPlaced raw : getPendingRawPlaced()) {
            files.put(WorldgenDefaults.PLACED_FEATURE_DIR
                    + WorldgenJson.sanitizeFileId(raw.featureId()) + WorldgenDefaults.JSON_SUFFIX, raw.json());
        }
        return files;
    }

    public @NotNull Map<String, String> buildBiomeModifierFiles() {
        Map<String, String> files = new ConcurrentHashMap<>();
        List<WorldgenServiceImpl.BiomeModification> mods = getPendingBiomeMods();
        for (int i = 0; i < mods.size(); i++) {
            WorldgenServiceImpl.BiomeModification mod = mods.get(i);
            String safe = WorldgenJson.sanitizeFileId(mod.biomeId());
            if (!mod.added().isEmpty()) {
                Map<String, List<String>> byStep = groupByStep(mod.added());
                for (Map.Entry<String, List<String>> group : byStep.entrySet()) {
                    String stepSafe = WorldgenJson.sanitizeFileId(group.getKey());
                    files.put(WorldgenDefaults.BIOME_MODIFIER_DIR + "modify_" + safe + "_" + i + "_"
                            + stepSafe + WorldgenDefaults.JSON_SUFFIX,
                            WorldgenModifierBuilder.buildAddFeaturesModifier(
                                    mod.biomes(), group.getValue(), group.getKey()));
                }
            }
            if (!mod.removed().isEmpty()) {
                files.put(WorldgenDefaults.BIOME_MODIFIER_DIR + "modify_" + safe + "_" + i
                        + "_remove" + WorldgenDefaults.JSON_SUFFIX,
                        WorldgenModifierBuilder.buildRemoveFeaturesModifier(
                                mod.biomes(), mod.removed(), mod.removeSteps()));
            }
        }
        for (WorldgenServiceImpl.CustomFeature feature : getPendingFeatures()) {
            String safe = WorldgenJson.sanitizeFileId(feature.featureId());
            files.put(WorldgenDefaults.CONFIGURED_FEATURE_DIR + safe + WorldgenDefaults.JSON_SUFFIX,
                    WorldgenJsonBuilder.buildConfiguredOreFeature(feature));
            files.put(WorldgenDefaults.PLACED_FEATURE_DIR + safe + WorldgenDefaults.JSON_SUFFIX,
                    WorldgenJsonBuilder.buildPlacedOreFeature(safe, feature));
            files.put(WorldgenDefaults.BIOME_MODIFIER_DIR + "add_" + safe + WorldgenDefaults.JSON_SUFFIX,
                    WorldgenModifierBuilder.buildAddFeaturesModifier(feature.biomes(),
                            List.of(WorldgenDefaults.NAMESPACE + safe), feature.step()));
        }
        return files;
    }

    private static @NotNull Map<String, List<String>> groupByStep(
            @NotNull List<WorldgenServiceImpl.AddedFeature> added) {
        Map<String, List<String>> byStep = new ConcurrentHashMap<>();
        for (WorldgenServiceImpl.AddedFeature add : added) {
            byStep.computeIfAbsent(add.step(), key -> newFeatureList()).add(add.feature());
        }
        return byStep;
    }

    private static @NotNull List<String> newFeatureList() {
        return new ArrayList<>();
    }

    public @NotNull Map<String, String> buildMixedVeinFiles() {
        Map<String, String> files = new ConcurrentHashMap<>();
        for (WorldgenServiceImpl.MixedVein vein : getPendingMixedVeins()) {
            String safe = WorldgenJson.sanitizeFileId(vein.veinId());
            files.put(WorldgenDefaults.CONFIGURED_FEATURE_DIR + safe + WorldgenDefaults.JSON_SUFFIX,
                    WorldgenJsonBuilder.buildMixedVeinConfiguredJson(vein));
            files.put(WorldgenDefaults.PLACED_FEATURE_DIR + safe + WorldgenDefaults.JSON_SUFFIX,
                    WorldgenJsonBuilder.buildPlacedFeatureJson(
                            WorldgenDefaults.NAMESPACE + safe, veinPlacement(vein)));
            files.put(WorldgenDefaults.BIOME_MODIFIER_DIR + "add_" + safe + WorldgenDefaults.JSON_SUFFIX,
                    WorldgenModifierBuilder.buildAddFeaturesModifier(vein.biomes(),
                            List.of(WorldgenDefaults.NAMESPACE + safe), vein.step()));
        }
        return files;
    }

    private static @NotNull WorldgenPlacement veinPlacement(
            @NotNull WorldgenServiceImpl.MixedVein vein) {
        return new WorldgenPlacement(vein.step(), vein.count(), vein.rarity(), vein.size(),
                vein.minHeight(), vein.maxHeight(), vein.heightType(),
                vein.anchorMin(), vein.anchorMax(), vein.discardChance(), vein.inner());
    }

    /** Delegates to the shared JSON builder; kept here as the public module entry point. */
    public @NotNull String buildVegetationConfiguredJson(
            @NotNull WorldgenServiceImpl.VegetationEntry veg) {
        return WorldgenJsonBuilder.buildVegetationConfiguredJson(veg);
    }
}
