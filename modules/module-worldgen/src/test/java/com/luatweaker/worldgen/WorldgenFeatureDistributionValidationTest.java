package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validation of the distribution primitives on an addFeature spec. */
class WorldgenFeatureDistributionValidationTest {

    private static final String PLAINS = WorldgenFixture.PLAINS;
    private static final String RUBY_ORE = WorldgenFixture.RUBY_ORE;

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void rejectsBadDistributionValues() {
        assertEquals(List.of(true, true, true, true, true, true, true),
            List.of(
                WorldgenFixture.rejects(() -> service.addFeature("mymod:a", baseSpec().put("heightType", "triangle"))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:b", baseSpec().put("anchorMin", "sea_level"))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:c", baseSpec().put("rarity", 0))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:d", baseSpec().put("discardChance", 1.5))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:e", baseSpec().put("discardChance", Double.NaN))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:f", baseSpec().put("scattered", "yes"))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:g", baseSpec().put("inner", -1)))),
            "triangle height type, sea_level anchor, zero rarity, out-of-range or NaN discard, "
                + "non-boolean scattered and negative inner must all be rejected");
    }

    @Test
    void rejectsZeroCountAndSize() {
        assertEquals(List.of(true, true),
            List.of(
                WorldgenFixture.rejects(() -> service.addFeature("mymod:count", baseSpec().put("count", 0))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:size", baseSpec().put("size", 0)))),
            "a zero count and a zero size must both be rejected");
    }

    @Test
    void rejectsUnknownStepAndReplace() {
        assertEquals(List.of(true, true),
            List.of(
                WorldgenFixture.rejects(() -> service.addFeature("mymod:step", baseSpec().put("step", "not_a_step"))),
                WorldgenFixture.rejects(() -> service.addFeature("mymod:replace", baseSpec().put("replace", "moon")))),
            "an unknown generation step and an unknown replace target must both be rejected");
    }

    private static FakeLuaTable baseSpec() {
        return new FakeLuaTable()
            .put("block", RUBY_ORE)
            .put("biomes", FakeLuaTables.listOf(PLAINS));
    }
}
