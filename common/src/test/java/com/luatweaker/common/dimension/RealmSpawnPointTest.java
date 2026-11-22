package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The custom spawn point from the config must be applied to the service. */
class RealmSpawnPointTest {

    private static DimensionServiceImpl.SpawnPoint spawnPoint;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        spawnPoint = DimensionShowcaseFixture.getService()
                .getSpawnPoint(DimensionShowcaseFixture.REALM_ID);
    }

    @Test
    void spawnPointIsSetFromConfig() {
        assertNotNull(spawnPoint,
                "spawn point must be set from config");
    }

    @Test
    void spawnPointXIsZero() {
        assertEquals(0, spawnPoint.x(),
                "the configured spawn x must be applied");
    }

    @Test
    void spawnPointZIsZero() {
        assertEquals(0, spawnPoint.z(),
                "the configured spawn z must be applied");
    }
}
