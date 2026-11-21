package com.luatweaker.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending-registration store shared by the worldgen service layers. The lists keep their
 * registration order (datapack output is deterministic); the override maps are keyed
 * lookups with no ordering contract, so they use ConcurrentHashMap for safe reload reads.
 */
class WorldgenStore {

    final List<WorldgenServiceImpl.OreEntry> pendingOres =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.VegetationEntry> pendingVegetation =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.OreRemovalEntry> pendingRemovals =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.BiomeModification> pendingBiomeMods =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.CustomFeature> pendingFeatures =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.RawConfigured> pendingRawConfigured =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.RawPlaced> pendingRawPlaced =
            Collections.synchronizedList(new ArrayList<>());
    final List<WorldgenServiceImpl.MixedVein> pendingMixedVeins =
            Collections.synchronizedList(new ArrayList<>());

    /** User overrides for ore-removal feature lists; block ID -&gt; placed-feature IDs. */
    final Map<String, List<String>> removalOverrides = new ConcurrentHashMap<>();

    /** Dimension ID -&gt; biome tag; seeded with the 3 vanilla defaults. */
    final Map<String, String> dimensionTags =
            new ConcurrentHashMap<>(WorldgenDefaults.DEFAULT_DIMENSION_TAGS);

    @NotNull
    public List<WorldgenServiceImpl.OreEntry> getPendingOres() {
        return Collections.unmodifiableList(pendingOres);
    }

    @NotNull
    public List<WorldgenServiceImpl.VegetationEntry> getPendingVegetation() {
        return Collections.unmodifiableList(pendingVegetation);
    }

    @NotNull
    public List<WorldgenServiceImpl.OreRemovalEntry> getPendingRemovals() {
        return Collections.unmodifiableList(pendingRemovals);
    }

    @NotNull
    public List<WorldgenServiceImpl.BiomeModification> getPendingBiomeMods() {
        return Collections.unmodifiableList(pendingBiomeMods);
    }

    @NotNull
    public List<WorldgenServiceImpl.CustomFeature> getPendingFeatures() {
        return Collections.unmodifiableList(pendingFeatures);
    }

    @NotNull
    public List<WorldgenServiceImpl.MixedVein> getPendingMixedVeins() {
        return Collections.unmodifiableList(pendingMixedVeins);
    }

    @NotNull
    public List<WorldgenServiceImpl.RawConfigured> getPendingRawConfigured() {
        return Collections.unmodifiableList(pendingRawConfigured);
    }

    @NotNull
    public List<WorldgenServiceImpl.RawPlaced> getPendingRawPlaced() {
        return Collections.unmodifiableList(pendingRawPlaced);
    }

    /** True when any pending registration (feature, raw, vein) already owns the id. */
    protected final boolean isFeatureIdTaken(@NotNull String featureId) {
        return pendingFeatures.stream().anyMatch(f -> f.featureId().equals(featureId))
                || pendingRawConfigured.stream().anyMatch(f -> f.featureId().equals(featureId))
                || pendingMixedVeins.stream().anyMatch(v -> v.veinId().equals(featureId));
    }

    public void clearAll() {
        pendingOres.clear();
        pendingVegetation.clear();
        pendingRemovals.clear();
        pendingBiomeMods.clear();
        pendingFeatures.clear();
        pendingMixedVeins.clear();
        pendingRawConfigured.clear();
        pendingRawPlaced.clear();
    }
}
