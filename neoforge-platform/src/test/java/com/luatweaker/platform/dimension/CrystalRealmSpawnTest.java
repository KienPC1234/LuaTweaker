package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teleport must find dry land near the crystal realm spawn point. */
class CrystalRealmSpawnTest {

    private static DimensionConfig realm;
    private static int spawnX;
    private static int spawnZ;
    private static DimensionShowcaseFixture.SpawnResult search;

    @BeforeAll
    static void searchAroundSpawn() {
        DimensionShowcaseFixture.ensureLoaded();
        realm = DimensionShowcaseFixture.getRealm();
        spawnX = realm.spawnX() != null ? realm.spawnX() : 0;
        spawnZ = realm.spawnZ() != null ? realm.spawnZ() : 0;
        search = DimensionShowcaseFixture.simulateSpawnSearch(realm, spawnX, spawnZ);
    }

    @Test
    void teleportFindsLandNearSpawn() {
        assertTrue(search.foundLand, "teleport must find a land column near spawn ("
                + spawnX + "," + spawnZ + ")");
    }

    @Test
    void landIsWithinTwoRingsOfSpawn() {
        assertTrue(search.ring <= 2,
                "land should be within 2 rings of the spawn point, found at ring " + search.ring);
    }

    @Test
    void landHeightStaysInWorldBounds() {
        assertTrue(search.landHeight >= realm.minHeight() + 2 && search.landHeight < realm.maxHeight(),
                "land height out of world bounds: " + search.landHeight);
    }

    @Test
    void landColumnIsAboveSeaLevel() {
        assertTrue(search.landHeight >= realm.seaLevel(),
                "land column must be above sea level (not underwater), got " + search.landHeight
                        + " (seaLevel " + realm.seaLevel() + ")");
    }
}
