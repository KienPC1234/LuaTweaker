package com.luatweaker.platform.dimension;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dimension blocks and NBT templates must be registered for World:PlaceStructure. */
class RealmContentNbtTest {

    private static final Set<String> EXPECTED_BLOCKS = Set.of("crystal_stone", "crystal_grass",
            "crystal_dirt", "crystal_peak", "crystal_log", "crystal_leaves",
            "luatweaker:crystal_portal", "luatweaker:crystal_sky_portal");

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    private static Set<String> registeredBlockIds() {
        Set<String> registeredBlocks = new HashSet<>();
        DimensionShowcaseFixture.getContentService().getRegisteredBlocks()
                .forEach(builder -> registeredBlocks.add(builder.getId()));
        return registeredBlocks;
    }

    @Test
    void dimensionBlocksAreRegistered() {
        assertTrue(registeredBlockIds().containsAll(EXPECTED_BLOCKS),
                "missing dimension blocks in: " + registeredBlockIds());
    }

    @Test
    void skyMonolithTemplateExists() {
        File monolithNbt = DimensionShowcaseFixture.findModFile("data/luatweaker_test/structures/sky_monolith.nbt");

        assertTrue(monolithNbt.exists(),
                "sky_monolith.nbt must exist for World:PlaceStructure");
    }

    @Test
    void crystalOutpostTemplateExists() {
        File outpostNbt = DimensionShowcaseFixture.findModFile("data/luatweaker_test/structures/crystal_outpost.nbt");

        assertTrue(outpostNbt.exists(),
                "crystal_outpost.nbt must exist for World:PlaceStructure");
    }

    @Test
    void crystalOutpostTemplateIsNotEmpty() {
        File outpostNbt = DimensionShowcaseFixture.findModFile("data/luatweaker_test/structures/crystal_outpost.nbt");

        assertTrue(outpostNbt.length() > 100,
                "crystal_outpost.nbt must not be empty");
    }
}
