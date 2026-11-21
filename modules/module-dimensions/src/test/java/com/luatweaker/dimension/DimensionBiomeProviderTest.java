package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies single-column biome provider storage and validation. */
class DimensionBiomeProviderTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void biomeProviderStoresFunction() {
        service.create(DIMENSION_ID, engine.createTable());
        engine.executeStringOrThrow("__biome = function(x, z) return 'arcane:crystal_plains' end",
                "biome_provider_fixture");
        service.setBiomeProvider(DIMENSION_ID, engine.getGlobalEnvironment().rawget("__biome"));

        assertNotNull(service.getBiomeProvider(DIMENSION_ID),
                "a stored biome provider must be readable back");
    }

    @Test
    void biomeProviderRejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setBiomeProvider(DIMENSION_ID, "nope"),
                "biome provider must be a Lua function");
    }
}
