package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Summary counts and clearAll semantics across every pending collection and registry. */
class WorldgenSummaryTest {

    private static final String RUBY_ORE = WorldgenFixture.RUBY_ORE;
    private static final String SAPPHIRE_ORE = "mymod:sapphire_ore";
    private static final String FLOWER = "mymod:flower";
    private static final String PLAINS = WorldgenFixture.PLAINS;
    private static final String COAL_ORE = "minecraft:coal_ore";
    private static final String OVERWORLD = WorldgenFixture.OVERWORLD;
    private static final String VEIN = "mymod:vein";
    private static final String RUBY_VEIN = "mymod:ruby_vein";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void modificationsCountOresVegetationAndRemovals() {
        service.addOre(RUBY_ORE, OVERWORLD, -64, 32, 8, 10);
        service.addOre(SAPPHIRE_ORE, OVERWORLD, -64, 16, 6, 5);
        service.addVegetation(FLOWER, 0.1, PLAINS);
        service.removeOre(COAL_ORE, OVERWORLD);

        Map<String, Object> summary = service.getModifications();

        assertEquals(List.of(2, 1, 1),
            List.of(summary.get("ores"), summary.get("vegetation"), summary.get("removals")),
            "the summary must count ores, vegetation and removals independently");
    }

    @Test
    void modificationsCountNewKinds() {
        service.modifyBiome(PLAINS, WorldgenFixture.removeOreDirtOptions());
        service.addFeature(RUBY_VEIN, WorldgenFixture.featureSpec());

        Map<String, Object> summary = service.getModifications();

        assertEquals(List.of(1, 1, 0),
            List.of(summary.get("biomeModifiers"), summary.get("customFeatures"), summary.get("ores")),
            "the summary must count biome modifiers and custom features separately from ores");
    }

    @Test
    void clearAllEmptiesCorePending() {
        service.addOre(RUBY_ORE, OVERWORLD, -64, 32, 8, 10);
        service.addVegetation(FLOWER, 0.1, PLAINS);
        service.removeOre(COAL_ORE, OVERWORLD);

        service.clearAll();

        assertEquals(List.of(true, true, true),
            List.of(
                service.getPendingOres().isEmpty(),
                service.getPendingVegetation().isEmpty(),
                service.getPendingRemovals().isEmpty()),
            "clearAll must empty the pending ores, vegetation and removals");
    }

    @Test
    void clearAllClearsMixedVeins() {
        service.addMixedVein(VEIN, WorldgenFixture.magnetiteVeinSpec());

        service.clearAll();

        assertEquals(List.of(true, true, 0),
            List.of(
                service.getPendingMixedVeins().isEmpty(),
                service.buildMixedVeinFiles().isEmpty(),
                service.getModifications().get("mixedVeins")),
            "clearAll must drop pending mixed veins and emit no vein files");
    }

    @Test
    void clearAllEmptiesNewPending() {
        service.modifyBiome(PLAINS, WorldgenFixture.removeOreDirtOptions());
        service.addFeature(RUBY_VEIN, WorldgenFixture.featureSpec());

        service.clearAll();

        assertEquals(List.of(true, true, true),
            List.of(
                service.getPendingBiomeMods().isEmpty(),
                service.getPendingFeatures().isEmpty(),
                service.buildBiomeModifierFiles().isEmpty()),
            "clearAll must drop pending biome modifiers and custom features");
    }

    @Test
    void clearAllKeepsRemovalRegistry() {
        service.registerRemovalFeatures(RUBY_ORE, "mymod:ruby_ore_upper");

        service.clearAll();

        assertEquals(List.of("mymod:ruby_ore_upper"), service.resolveRemovalFeatures(RUBY_ORE),
            "clearAll must keep the removal-feature registry");
    }

    @Test
    void clearAllKeepsDimensionRegistry() {
        service.registerDimensionBiomeTag("mymod:deep", "#mymod:is_deep");

        service.clearAll();

        assertEquals(Optional.of("#mymod:is_deep"), service.resolveDimensionBiomeTag("mymod:deep"),
            "clearAll must keep the dimension biome-tag registry");
    }
}
