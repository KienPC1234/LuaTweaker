package com.luatweaker.loot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableJsonBuilderFishingTest {

    private static final String COD = "minecraft:cod";
    private static final String FISH_SEQUENCE = "minecraft:gameplay/fishing/fish";

    private static final String FISHING_TYPE = "\"type\":\"minecraft:fishing\"";
    private static final String BONUS_ROLLS = "\"bonus_rolls\":0.0";
    private static final String ROLLS = "\"rolls\":1.0";
    private static final String RANDOM_SEQUENCE = "\"random_sequence\":\"minecraft:gameplay/fishing/fish\"";
    private static final String COD_NAME = "\"name\":\"minecraft:cod\"";
    private static final String WEIGHT = "\"weight\":50";

    private String fishingJson;

    @BeforeEach
    void setup() {
        LootServiceImpl.FishingLootEntry entry =
            new LootServiceImpl.FishingLootEntry(COD, 0.5, "FISH");
        fishingJson = LootTableJsonBuilder.buildFishingLootTableJson(List.of(entry), FISH_SEQUENCE);
    }

    @Test
    void fishingTableIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(fishingJson),
            "the generated fishing loot table must be structurally valid JSON");
    }

    @Test
    void fishingTableUsesSubtableEnvelope() {
        assertTrue(JsonAssertions.containsAll(fishingJson, FISHING_TYPE, BONUS_ROLLS, ROLLS,
                RANDOM_SEQUENCE, COD_NAME, WEIGHT),
            "the fishing subtable must pin type, rolls, random sequence, item name and weight");
    }

    @Test
    void fishingTableRejectsBlankRandomSequence() {
        LootServiceImpl.FishingLootEntry entry =
            new LootServiceImpl.FishingLootEntry(COD, 0.5, "FISH");

        assertThrows(IllegalArgumentException.class,
            () -> LootTableJsonBuilder.buildFishingLootTableJson(List.of(entry), "  "),
            "a blank random sequence must be rejected");
    }
}
