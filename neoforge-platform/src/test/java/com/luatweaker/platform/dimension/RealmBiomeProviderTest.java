package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lua biome provider must return only configured biome ids. */
class RealmBiomeProviderTest {

    private static final int PROBE_COUNT = 32;

    private static DimensionConfig config;
    private static DimensionServiceImpl service;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        config = DimensionShowcaseFixture.getRealm();
        service = DimensionShowcaseFixture.getService();
    }

    private static Set<String> realmBiomeIds() {
        Set<String> biomeIds = new HashSet<>();
        for (DimensionConfig.BiomeEntry biome : config.biomes()) {
            biomeIds.add(biome.id());
        }
        return biomeIds;
    }

    @Test
    void biomeProviderIsSet() {
        assertNotNull(service.getBiomeProvider(DimensionShowcaseFixture.REALM_ID),
                "biome provider must be set");
    }

    @Test
    void biomeProviderAlwaysReturnsABiome() {
        for (int index = 0; index < PROBE_COUNT; index++) {
            String biome = service.computeBiome(DimensionShowcaseFixture.REALM_ID,
                    index * 31, index * 17);

            assertNotNull(biome,
                    "biome provider must return a biome id");
        }
    }

    @Test
    void biomeProviderReturnsOnlyConfiguredIds() {
        Set<String> biomeIds = realmBiomeIds();
        for (int index = 0; index < PROBE_COUNT; index++) {
            String biome = service.computeBiome(DimensionShowcaseFixture.REALM_ID,
                    index * 31, index * 17);

            assertTrue(biomeIds.contains(biome),
                    "unexpected biome id: " + biome);
        }
    }
}
