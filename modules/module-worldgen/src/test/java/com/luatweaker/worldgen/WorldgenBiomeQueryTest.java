package com.luatweaker.worldgen;

import com.luatweaker.api.pal.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** getBiome reads the live platform registry and validates the id first. */
class WorldgenBiomeQueryTest {

    private static final String PLAINS = "minecraft:plains";
    private static final String INVALID_BIOME = "Not A Biome";
    private static final String TEMPERATURE_KEY = "temperature";
    private static final String SPAWNERS_KEY = "spawners";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @AfterEach
    void clearPlatformWorldgen() {
        Platform.setWorldgen(null);
    }

    @Test
    void biomeReturnsPlatformRegistryData() {
        Map<String, Object> info = Map.of("id", PLAINS, TEMPERATURE_KEY, 0.8d, SPAWNERS_KEY, Map.of());
        Platform.setWorldgen(id -> PLAINS.equals(id) ? info : null);

        assertEquals(info, service.getBiome(PLAINS),
            "getBiome must return the platform's registry data for a known biome");
    }

    @Test
    void unknownBiomeIsRejected() {
        Platform.setWorldgen(id -> null);

        String message = WorldgenFixture.errorMessage(() -> service.getBiome(PLAINS));

        assertTrue(message.contains("Unknown biome"),
            "an unknown biome must be reported loudly, got: " + message);
    }

    @Test
    void invalidBiomeIdIsRejectedBeforeQuerying() {
        Platform.setWorldgen(id -> {
            throw new AssertionError("an invalid id must not reach the platform");
        });

        assertThrows(IllegalArgumentException.class, () -> service.getBiome(INVALID_BIOME),
            "an invalid biome id must be rejected before the platform is queried");
    }
}
