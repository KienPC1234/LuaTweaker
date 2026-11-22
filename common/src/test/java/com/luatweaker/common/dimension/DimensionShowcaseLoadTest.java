package com.luatweaker.common.dimension;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The showcase LuaMods must load once without errors and register dimensions. */
class DimensionShowcaseLoadTest {

    @BeforeAll
    static void loadShowcaseMods() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void luamodsDirectoryExists() {
        assertTrue(DimensionShowcaseFixture.getBaseDir().exists(),
                "luamods directory should exist at "
                        + DimensionShowcaseFixture.getBaseDir().getAbsolutePath());
    }

    @Test
    void modsLoadWithoutErrors() {
        assertTrue(DimensionShowcaseFixture.getLoadErrors().isEmpty(),
                "LuaMods must load without errors: " + DimensionShowcaseFixture.getLoadErrors());
    }

    @Test
    void dimensionServiceIsRegistered() {
        assertTrue(DimensionShowcaseFixture.getService() instanceof DimensionServiceImpl,
                "DimensionServiceImpl must be registered");
    }

    @Test
    void crystalRealmIsRegistered() {
        assertNotNull(DimensionShowcaseFixture.getRealm(),
                "crystal_realm must be registered");
    }

    @Test
    void crystalSkylandsIsRegistered() {
        assertNotNull(DimensionShowcaseFixture.getSkylands(),
                "crystal_skylands must be registered");
    }
}
