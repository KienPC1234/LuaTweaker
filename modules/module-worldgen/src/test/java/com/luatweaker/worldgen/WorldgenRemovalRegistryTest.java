package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla removal defaults and custom overrides. */
class WorldgenRemovalRegistryTest {

    private static final String COAL_ORE = "minecraft:coal_ore";
    private static final String QUARTZ_ORE = "minecraft:nether_quartz_ore";
    private static final String ANCIENT_DEBRIS = "minecraft:ancient_debris";
    private static final String RUBY_ORE = "mymod:ruby_ore";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void coalOreResolvesToBothVanillaFeatures() {
        assertEquals(List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower"),
            service.resolveRemovalFeatures(COAL_ORE),
            "coal ore must resolve to both vanilla coal features");
    }

    @Test
    void quartzOreResolvesToNetherFeature() {
        assertEquals(List.of("minecraft:ore_quartz_nether"),
            service.resolveRemovalFeatures(QUARTZ_ORE),
            "nether quartz ore must resolve to its single vanilla feature");
    }

    @Test
    void ancientDebrisResolvesToBothVanillaFeatures() {
        assertEquals(List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small"),
            service.resolveRemovalFeatures(ANCIENT_DEBRIS),
            "ancient debris must resolve to both vanilla debris features");
    }

    @Test
    void registersCustomRemovalFeatures() {
        assertTrue(service.registerRemovalFeatures(RUBY_ORE, "mymod:ruby_ore_upper", "mymod:ruby_ore_lower"),
            "registerRemovalFeatures must report success for a valid registration");
    }

    @Test
    void resolvesCustomRemovalFeatures() {
        service.registerRemovalFeatures(RUBY_ORE, "mymod:ruby_ore_upper", "mymod:ruby_ore_lower");

        assertEquals(List.of("mymod:ruby_ore_upper", "mymod:ruby_ore_lower"),
            service.resolveRemovalFeatures(RUBY_ORE),
            "a custom registration must resolve in the supplied order");
    }

    @Test
    void overridesVanillaRemovalFeatures() {
        service.registerRemovalFeatures(COAL_ORE, "mymod:coal_vein");

        assertEquals(List.of("mymod:coal_vein"),
            service.resolveRemovalFeatures(COAL_ORE),
            "a custom registration must replace the vanilla default for that block");
    }
}
