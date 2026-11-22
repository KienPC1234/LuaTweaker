package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teleport must find a floating island near the skylands spawn point. */
class CrystalSkylandsSpawnTest {

    private static DimensionConfig skylands;
    private static int spawnX;
    private static int spawnZ;
    private static DimensionShowcaseFixture.SpawnResult search;

    @BeforeAll
    static void searchAroundSpawn() {
        DimensionShowcaseFixture.ensureLoaded();
        skylands = DimensionShowcaseFixture.getSkylands();
        spawnX = skylands.spawnX() != null ? skylands.spawnX() : 0;
        spawnZ = skylands.spawnZ() != null ? skylands.spawnZ() : 0;
        search = DimensionShowcaseFixture.simulateSpawnSearch(skylands, spawnX, spawnZ);
    }

    @Test
    void teleportFindsIslandNearSpawn() {
        assertTrue(search.foundLand, "skylands teleport must find an island near spawn ("
                + spawnX + "," + spawnZ + ")");
    }

    @Test
    void islandIsWithinFourRingsOfSpawn() {
        assertTrue(search.ring <= 4,
                "island should be close to the spawn point, found at ring " + search.ring);
    }

    @Test
    void islandTopIsAtSkylandAltitude() {
        assertTrue(search.landHeight >= 180,
                "island top must be at the skyland altitude, got " + search.landHeight);
    }

    @Test
    void islandTopStaysBelowWorldHeight() {
        assertTrue(search.landHeight < skylands.maxHeight(),
                "island above world height: " + search.landHeight);
    }
}
