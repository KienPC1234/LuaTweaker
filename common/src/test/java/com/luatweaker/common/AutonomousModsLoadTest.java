package com.luatweaker.common;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The autonomous LuaMods must load once without errors. */
class AutonomousModsLoadTest {

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
    }

    @AfterAll
    static void tearDown() {
        AutonomousModsFixture.shutdownLogger();
    }

    @Test
    void luamodsDirectoryExists() {
        assertTrue(AutonomousModsFixture.getBaseDir().exists(),
                "luamods directory should exist at "
                        + AutonomousModsFixture.getBaseDir().getAbsolutePath());
    }

    @Test
    void modsLoadWithoutErrors() {
        assertTrue(AutonomousModsFixture.getLoadErrors().isEmpty(),
                "Failed executing autonomous LuaMods in "
                        + AutonomousModsFixture.getBaseDir().getAbsolutePath()
                        + ": " + AutonomousModsFixture.getLoadErrors());
    }

    @Test
    void dimensionServiceIsRegistered() {
        assertTrue(AutonomousModsFixture.getDimensions() != null,
                "DimensionServiceImpl should be registered after loading the test mod");
    }
}
