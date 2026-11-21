package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vegetation flower and patch registration plus their configured-feature JSON. */
class WorldgenVegetationTest {

    private static final String FLOWER = "mymod:crystal_flower";
    private static final String GRASS = "minecraft:short_grass";
    private static final String PLAINS = "minecraft:plains";

    private static final String FLOWER_TYPE = "\"type\":\"minecraft:flower\"";
    private static final String RANDOM_PATCH_TYPE = "\"type\":\"minecraft:random_patch\"";
    private static final String STATE_PROVIDER = "\"type\":\"minecraft:simple_state_provider\"";
    private static final String BLOCK_PREDICATE = "\"type\":\"minecraft:block_predicate_filter\"";
    private static final String FLOWER_STATE = "\"state\":{\"Name\":\"mymod:crystal_flower\"}";
    private static final String GRASS_STATE = "\"state\":{\"Name\":\"minecraft:short_grass\"}";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void addVegetationStoresFlowerEntry() {
        service.addVegetation(FLOWER, 0.1, PLAINS);

        assertEquals(List.of(new WorldgenServiceImpl.VegetationEntry(FLOWER, 0.1, List.of(PLAINS), "flower")),
            service.getPendingVegetation(),
            "addVegetation must store the entry with the flower kind");
    }

    @Test
    void addVegetationPatchStoresPatchEntry() {
        service.addVegetationPatch(GRASS, 0.2, PLAINS);

        assertEquals(List.of(new WorldgenServiceImpl.VegetationEntry(GRASS, 0.2, List.of(PLAINS), "patch")),
            service.getPendingVegetation(),
            "addVegetationPatch must store the entry with the patch kind");
    }

    @Test
    void flowerConfiguredJsonUsesTypedProvider() {
        service.addVegetation(FLOWER, 0.1, PLAINS);

        String json = service.buildVegetationConfiguredJson(service.getPendingVegetation().get(0));

        assertTrue(JsonFragments.containsAll(json, FLOWER_TYPE, "\"tries\":64", STATE_PROVIDER,
                FLOWER_STATE, BLOCK_PREDICATE),
            "the flower configured feature must use the typed state provider: " + json);
    }

    @Test
    void patchConfiguredJsonUsesRandomPatch() {
        service.addVegetationPatch(GRASS, 0.2, PLAINS);

        String json = service.buildVegetationConfiguredJson(service.getPendingVegetation().get(0));

        assertTrue(JsonFragments.containsAll(json, RANDOM_PATCH_TYPE, "\"tries\":32", GRASS_STATE),
            "a patch must emit the random_patch feature with its block state: " + json);
    }

    @Test
    void patchConfiguredJsonOmitsFlowerType() {
        service.addVegetationPatch(GRASS, 0.2, PLAINS);

        String json = service.buildVegetationConfiguredJson(service.getPendingVegetation().get(0));

        assertTrue(JsonFragments.containsNone(json, FLOWER_TYPE),
            "a patch must not be emitted as a flower feature: " + json);
    }

    @Test
    void addVegetationRejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addVegetation(FLOWER, 1.5, PLAINS),
            "a vegetation chance above 1.0 must be rejected");
    }

    @Test
    void addVegetationRejectsEmptyBiomes() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addVegetation(FLOWER, 0.5),
            "vegetation without any biome must be rejected");
    }

    @Test
    void addVegetationPatchRejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addVegetationPatch(GRASS, 1.5, PLAINS),
            "a patch chance above 1.0 must be rejected");
    }
}
