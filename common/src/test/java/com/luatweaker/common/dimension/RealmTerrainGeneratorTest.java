package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lua terrain generator must produce sane heights with known blocks. */
class RealmTerrainGeneratorTest {

    private static final int PROBE_COUNT = 64;
    private static final Set<String> KNOWN_BLOCKS = Set.of(
            "luatweaker:crystal_stone", "luatweaker:crystal_dirt", "luatweaker:crystal_peak");

    private static DimensionConfig config;
    private static DimensionServiceImpl service;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        config = DimensionShowcaseFixture.getRealm();
        service = DimensionShowcaseFixture.getService();
    }

    @Test
    void terrainGeneratorIsSet() {
        assertNotNull(service.getTerrainGenerator(DimensionShowcaseFixture.REALM_ID),
                "terrain generator must be set");
    }

    @Test
    void terrainHeightsStayInRange() {
        for (int index = 0; index < PROBE_COUNT; index++) {
            DimensionServiceImpl.TerrainResult terrain = service.computeTerrain(
                    DimensionShowcaseFixture.REALM_ID, index * 7, index * 13, config.seaLevel());

            assertTrue(terrain.height() >= config.minHeight() + 1 && terrain.height() < config.maxHeight(),
                    "height out of range: " + terrain.height());
        }
    }

    @Test
    void terrainUsesKnownBlocks() {
        for (int index = 0; index < PROBE_COUNT; index++) {
            DimensionServiceImpl.TerrainResult terrain = service.computeTerrain(
                    DimensionShowcaseFixture.REALM_ID, index * 7, index * 13, config.seaLevel());

            assertTrue(KNOWN_BLOCKS.contains(terrain.blockId()),
                    "unexpected block: " + terrain.blockId());
        }
    }

    @Test
    void terrainVariesAcrossTheMap() {
        int near = service.computeTerrain(
                DimensionShowcaseFixture.REALM_ID, 0, 0, config.seaLevel()).height();
        int far = service.computeTerrain(
                DimensionShowcaseFixture.REALM_ID, 1000, 1000, config.seaLevel()).height();

        assertNotEquals(near, far,
                "terrain must vary across the map");
    }
}
