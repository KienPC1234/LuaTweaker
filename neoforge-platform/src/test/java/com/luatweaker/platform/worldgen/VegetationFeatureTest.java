package com.luatweaker.platform.worldgen;

import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vegetation must emit one placed feature per biome with its own modifier. */
class VegetationFeatureTest {

    private static Map<String, String> vegetationFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addVegetation("mymod:crystal_flower", 0.1,
                "minecraft:plains", "minecraft:desert");
        return WorldgenFixtures.apply(service);
    }

    @Test
    void emitsOnePlacedFeaturePerBiome() {
        long placed = vegetationFiles().keySet().stream()
                .filter(key -> key.contains("placed_feature/")).count();

        assertEquals(2, placed,
                "one placed feature per biome (multiple 'biome' placements would AND together)");
    }

    @Test
    void emitsOneBiomePlacementPerBiome() {
        long biomePlacements = vegetationFiles().values().stream()
                .filter(json -> json.contains("\"type\":\"minecraft:biome\""))
                .count();

        assertEquals(2, biomePlacements,
                "one biome placement per configured biome");
    }

    @Test
    void bareBiomeShapeStaysUnscoped() {
        // Bare minecraft:biome is the vanilla shape (191/191 placed features in the
        // 1.21.1 jar) - per-biome scoping lives in the add_features modifier, not here.
        assertFalse(vegetationFiles().values().stream()
                        .anyMatch(json -> json.contains("\"biome\":\"minecraft:plains\"")),
                "the bare biome placement must not hardcode a biome");
    }

    @Test
    void emitsOneModifierPerPlacedFeature() {
        long vegModifiers = vegetationFiles().values().stream()
                .filter(json -> json.contains("\"type\":\"neoforge:add_features\"")
                        && json.contains("\"step\":\"vegetal_decoration\""))
                .count();

        assertEquals(2, vegModifiers,
                "one add_features modifier per placed vegetation feature");
    }

    @Test
    void modifierScopesPlains() {
        assertTrue(vegetationFiles().values().stream()
                        .anyMatch(json -> json.contains("\"biomes\":[\"minecraft:plains\"]")),
                "one modifier must scope the plains biome");
    }

    @Test
    void modifierScopesDesert() {
        assertTrue(vegetationFiles().values().stream()
                        .anyMatch(json -> json.contains("\"biomes\":[\"minecraft:desert\"]")),
                "one modifier must scope the desert biome");
    }

    @Test
    void chanceComesFromConfig() {
        assertTrue(vegetationFiles().values().stream().anyMatch(json -> json.contains("\"chance\":10")),
                "the placement chance must come from the config");
    }
}
