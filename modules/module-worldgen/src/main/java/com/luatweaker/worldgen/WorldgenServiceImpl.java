package com.luatweaker.worldgen;

import com.luatweaker.api.worldgen.IWorldgenService;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldgenServiceImpl implements IWorldgenService {

    public record OreEntry(String blockId, String dimension, int minHeight, int maxHeight,
                           int clusterSize, int frequency, List<String> biomes) {}

    public record VegetationEntry(String blockId, double chance, List<String> biomes) {}

    public record OreRemovalEntry(String blockId, String dimension) {}

    private final List<OreEntry> pendingOres = Collections.synchronizedList(new ArrayList<>());
    private final List<VegetationEntry> pendingVegetation = Collections.synchronizedList(new ArrayList<>());
    private final List<OreRemovalEntry> pendingRemovals = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void addOre(@NotNull String blockId, @NotNull String dimension,
                       int minHeight, int maxHeight, int clusterSize, int frequency) {
        validate(blockId, dimension, minHeight, maxHeight, clusterSize, frequency);
        pendingOres.add(new OreEntry(blockId, dimension, minHeight, maxHeight, clusterSize, frequency, List.of()));
    }

    @Override
    public void addOreBiomeFiltered(@NotNull String blockId, @NotNull String dimension,
                                    int minHeight, int maxHeight, int clusterSize, int frequency,
                                    @NotNull String[] biomes) {
        validate(blockId, dimension, minHeight, maxHeight, clusterSize, frequency);
        if (biomes.length == 0) {
            throw new IllegalArgumentException("biomes must not be empty for biome-filtered ore");
        }
        pendingOres.add(new OreEntry(blockId, dimension, minHeight, maxHeight, clusterSize, frequency, List.of(biomes)));
    }

    @Override
    public void addVegetation(@NotNull String blockId, double chance, @NotNull String[] biomes) {
        if (blockId.isBlank()) throw new IllegalArgumentException("blockId must not be blank");
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0, got: " + chance);
        }
        if (biomes.length == 0) {
            throw new IllegalArgumentException("biomes must not be empty");
        }
        pendingVegetation.add(new VegetationEntry(blockId, chance, List.of(biomes)));
    }

    @Override
    public boolean removeOre(@NotNull String blockId, @NotNull String dimension) {
        if (blockId.isBlank() || dimension.isBlank()) {
            throw new IllegalArgumentException("blockId and dimension must not be blank");
        }
        pendingRemovals.add(new OreRemovalEntry(blockId, dimension));
        return true;
    }

    @Override
    @NotNull
    public Map<String, Object> getModifications() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ores", pendingOres.size());
        summary.put("vegetation", pendingVegetation.size());
        summary.put("removals", pendingRemovals.size());
        return summary;
    }

    @Override
    public void clearAll() {
        pendingOres.clear();
        pendingVegetation.clear();
        pendingRemovals.clear();
    }

    public List<OreEntry> getPendingOres() {
        return Collections.unmodifiableList(pendingOres);
    }

    public List<VegetationEntry> getPendingVegetation() {
        return Collections.unmodifiableList(pendingVegetation);
    }

    public List<OreRemovalEntry> getPendingRemovals() {
        return Collections.unmodifiableList(pendingRemovals);
    }

    private void validate(String blockId, String dimension, int minHeight, int maxHeight,
                          int clusterSize, int frequency) {
        if (blockId.isBlank()) throw new IllegalArgumentException("blockId must not be blank");
        if (dimension.isBlank()) throw new IllegalArgumentException("dimension must not be blank");
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") > maxHeight (" + maxHeight + ")");
        }
        if (clusterSize <= 0) throw new IllegalArgumentException("clusterSize must be > 0, got: " + clusterSize);
        if (frequency <= 0) throw new IllegalArgumentException("frequency must be > 0, got: " + frequency);
    }
}
