package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skylands pipeline must shape floating islands over void. */
class SkylandsShapeTest {

    private static DimensionServiceImpl service;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        service = DimensionShowcaseFixture.getService();
    }

    @Test
    void terrainGeneratorIsSet() {
        assertNotNull(service.getTerrainGenerator(DimensionShowcaseFixture.SKYLANDS_ID),
                "skylands terrain generator must be set (islands shaped in Lua)");
    }

    @Test
    void blockPickerIsSet() {
        assertNotNull(service.getBlockPicker(DimensionShowcaseFixture.SKYLANDS_ID),
                "skylands block picker must be set (void carved in Lua)");
    }

    @Test
    void spawnXIsZero() {
        assertEquals(0, service.getSpawnPoint(DimensionShowcaseFixture.SKYLANDS_ID).x(),
                "the skylands spawn x must come from the config");
    }

    @Test
    void portalTargetResolvesSkyPortal() {
        assertEquals(DimensionShowcaseFixture.SKYLANDS_ID,
                service.getPortalTarget("luatweaker:crystal_sky_portal"),
                "the sky portal target must resolve to the skylands");
    }

    @Test
    void dimensionIdsContainSkylands() {
        assertTrue(service.getDimensionIds().contains(DimensionShowcaseFixture.SKYLANDS_ID),
                "both dimensions must be registered");
    }
}
