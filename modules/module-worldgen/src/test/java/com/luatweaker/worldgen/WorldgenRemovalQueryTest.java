package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Removal-feature queries: defaults, overrides, unknown blocks and validation. */
class WorldgenRemovalQueryTest {

    private static final String COAL_ORE = "minecraft:coal_ore";
    private static final String RUBY_ORE = "mymod:ruby_ore";
    private static final String UNKNOWN_ORE = "mymod:unknown_ore";
    private static final String UNKNOWN_MOD_ORE = "unknownmod:unknown_ore";
    private static final String UPPER_FEATURE = "mymod:ruby_ore_upper";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void registerRemovalFeaturesRejectsBadInput() {
        assertEquals(List.of(true, true, true),
            List.of(
                WorldgenFixture.rejects(() -> service.registerRemovalFeatures("", UPPER_FEATURE)),
                WorldgenFixture.rejects(() -> service.registerRemovalFeatures(RUBY_ORE)),
                WorldgenFixture.rejects(() -> service.registerRemovalFeatures(RUBY_ORE, "not a feature!!"))),
            "a blank block id, an empty feature list and an invalid feature id must all be rejected");
    }

    @Test
    void unknownBlockResolvesToEmpty() {
        assertTrue(service.resolveRemovalFeatures(UNKNOWN_MOD_ORE).isEmpty(),
            "an unknown block must resolve to no removal features");
    }

    @Test
    void vanillaDefaultsAreReturned() {
        assertEquals(List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower"),
            service.getRemovalFeatures(COAL_ORE),
            "getRemovalFeatures must return the vanilla defaults for coal ore");
    }

    @Test
    void overridesArePreferred() {
        service.registerRemovalFeatures(RUBY_ORE, UPPER_FEATURE);

        assertEquals(List.of(UPPER_FEATURE), service.getRemovalFeatures(RUBY_ORE),
            "getRemovalFeatures must return a registered override");
    }

    @Test
    void unknownBlockReturnsEmpty() {
        assertTrue(service.getRemovalFeatures(UNKNOWN_ORE).isEmpty(),
            "getRemovalFeatures must return no features for an unknown block");
    }

    @Test
    void invalidBlockIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.getRemovalFeatures("Not A Resource!"),
            "getRemovalFeatures must reject an invalid block id");
    }
}
