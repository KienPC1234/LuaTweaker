package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lua block picker must produce only valid in-range overrides. */
class RealmBlockPickerTest {

    private static final int PROBE_COUNT = 128;
    private static final Set<String> VALID_BLOCK_IDS = Set.of("minecraft:air", "minecraft:lava",
            "luatweaker:crystal_ore", "luatweaker:crystal_grass",
            "luatweaker:crystal_dirt", "luatweaker:crystal_stone", "luatweaker:crystal_peak",
            "luatweaker:crystal_log", "luatweaker:crystal_leaves");

    private record OverrideSample(int y, String block) {
    }

    private static List<OverrideSample> collectOverrides() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig config = DimensionShowcaseFixture.getRealm();
        List<OverrideSample> samples = new ArrayList<>();
        for (int index = 0; index < PROBE_COUNT; index++) {
            Map<Integer, String> overrides = service.computeBlockOverrides(
                    DimensionShowcaseFixture.REALM_ID, index * 3, index * 5,
                    config.seaLevel() + 10, config.minHeight(), config.maxHeight());
            for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
                samples.add(new OverrideSample(entry.getKey(), entry.getValue()));
            }
        }
        return samples;
    }

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    @Test
    void blockPickerIsSet() {
        assertNotNull(DimensionShowcaseFixture.getService()
                        .getBlockPicker(DimensionShowcaseFixture.REALM_ID),
                "block picker must be set");
    }

    @Test
    void overrideHeightsStayInRange() {
        DimensionConfig config = DimensionShowcaseFixture.getRealm();
        for (OverrideSample sample : collectOverrides()) {
            assertTrue(sample.y() >= config.minHeight() && sample.y() < config.maxHeight(),
                    "override Y out of range: " + sample.y());
        }
    }

    @Test
    void overrideBlocksAreKnown() {
        for (OverrideSample sample : collectOverrides()) {
            assertTrue(VALID_BLOCK_IDS.contains(sample.block()),
                    "unexpected override block: " + sample.block());
        }
    }

    @Test
    void pickerProducesOverridesSomewhere() {
        assertFalse(collectOverrides().isEmpty(),
                "the picker must produce overrides somewhere");
    }
}
