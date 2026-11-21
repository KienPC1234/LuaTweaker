package com.luatweaker.worldgen;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.worldgen.IWorldgenService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registration-state queries and override registrations. Sits between the pending store
 * and the file-plan layer so each class stays a small, single-purpose collaborator.
 */
abstract class AbstractWorldgenConfig extends WorldgenStore
        implements IWorldgenService, IWorldgenExtendedService {

    @Override
    public boolean registerRemovalFeatures(@NotNull String blockId, @NotNull String... features) {
        WorldgenValidation.validateResourceLocation(blockId, "blockId");
        if (features.length == 0) {
            throw new IllegalArgumentException("features must not be empty for '" + blockId + "'");
        }
        for (String feature : features) {
            WorldgenValidation.validateResourceLocation(feature, "features entry");
        }
        removalOverrides.put(blockId, List.of(features));
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[Worldgen] Registered removal features for '" + blockId + "': " + List.of(features));
        return true;
    }

    @Override
    public boolean registerDimensionBiomeTag(@NotNull String dimension, @NotNull String biomeTag) {
        WorldgenValidation.validateResourceLocation(dimension, "dimension");
        if (biomeTag == null || biomeTag.isEmpty()) {
            throw new IllegalArgumentException("biomeTag must not be empty");
        }
        if (biomeTag.startsWith("#")) {
            WorldgenValidation.validateResourceLocation(biomeTag.substring(1), "biomeTag target");
        } else {
            WorldgenValidation.validateBiomeRef(biomeTag);
        }
        dimensionTags.put(dimension, biomeTag);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[Worldgen] Registered dimension '" + dimension + "' with biome tag '" + biomeTag + "'");
        return true;
    }

    @Override
    public @NotNull Map<String, String> getKnownDimensions() {
        return Map.copyOf(dimensionTags);
    }

    @Override
    public @NotNull Map<String, Object> getBiome(@NotNull String biomeId) {
        WorldgenValidation.validateResourceLocation(biomeId, "biomeId");
        Map<String, Object> info = Platform.getWorldgen().getBiomeInfo(biomeId);
        if (info == null) {
            throw new IllegalArgumentException("Unknown biome '" + biomeId
                    + "': it is not present in the server biome registry (is a server running?)");
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM, "[Worldgen] Queried biome '" + biomeId + "'");
        return Collections.unmodifiableMap(info);
    }

    @Override
    public @NotNull List<String> getRemovalFeatures(@NotNull String blockId) {
        WorldgenValidation.validateResourceLocation(blockId, "blockId");
        return List.copyOf(resolveRemovalFeatures(blockId));
    }

    @Override
    public @Nullable String getDimensionBiomeTag(@NotNull String dimension) {
        WorldgenValidation.validateResourceLocation(dimension, "dimension");
        return resolveDimensionBiomeTag(dimension).orElse(null);
    }

    @Override
    public @NotNull Map<String, Object> getModifications() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("ores", pendingOres.size());
        summary.put("vegetation", pendingVegetation.size());
        summary.put("removals", pendingRemovals.size());
        summary.put("biomeModifiers", pendingBiomeMods.size());
        summary.put("customFeatures", pendingFeatures.size());
        summary.put("mixedVeins", pendingMixedVeins.size());
        summary.put("rawConfigured", pendingRawConfigured.size());
        summary.put("rawPlaced", pendingRawPlaced.size());
        return summary;
    }

    /**
     * Placed-feature IDs to strip for an ore block: user override first, then the vanilla
     * defaults. Empty = unknown block; the caller must skip loudly instead of emitting a
     * modifier that references nothing.
     */
    public @NotNull List<String> resolveRemovalFeatures(@NotNull String blockId) {
        List<String> override = removalOverrides.get(blockId);
        if (override != null) {
            return override;
        }
        List<String> defaults = WorldgenDefaults.DEFAULT_REMOVAL_FEATURES.get(blockId);
        return defaults != null ? defaults : List.of();
    }

    /**
     * Biome tag (or single biome ID) for a dimension. Empty = the dimension is not
     * registered; the caller must verify existence platform-side and skip loudly instead
     * of emitting a modifier with a dangling selector.
     */
    public @NotNull Optional<String> resolveDimensionBiomeTag(@NotNull String dimension) {
        return Optional.ofNullable(dimensionTags.get(dimension));
    }
}
