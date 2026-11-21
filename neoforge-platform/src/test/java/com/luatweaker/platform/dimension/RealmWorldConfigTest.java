package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** World-shape fields must come from the Lua config file. */
class RealmWorldConfigTest {

    private static DimensionConfig realm;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        realm = DimensionShowcaseFixture.getRealm();
    }

    @Test
    void bedrockComesFromConfig() {
        assertTrue(realm.hasBedrock(),
                "has_bedrock must come from the config file");
    }

    @Test
    void biomeSizeComesFromConfig() {
        assertEquals(8, realm.biomeSize(),
                "biome_size must come from the config file");
    }

    @Test
    void spawnXComesFromConfig() {
        assertEquals(0, realm.spawnX(),
                "spawn_x must come from the config file");
    }

    @Test
    void spawnZComesFromConfig() {
        assertEquals(0, realm.spawnZ(),
                "spawn_z must come from the config file");
    }

    @Test
    void skyLightIsEnabled() {
        assertTrue(realm.hasSkyLight(),
                "the crystal realm is a surface world with skylight");
    }

    @Test
    void threeBiomesAreConfigured() {
        assertEquals(3, realm.biomes().size(),
                "3 biomes expected");
    }

    @Test
    void fiveSpawnerEntriesAreConfigured() {
        assertEquals(5, realm.spawnEntities().size(),
                "5 vanilla spawner entries expected");
    }

    @Test
    void skyColorIsAValidColor() {
        assertTrue(DimensionConfig.isColor(realm.skyColor()),
                "the sky color must parse as a real color");
    }
}
