package com.luatweaker.loot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableJsonBuilderBlockTest {

    private static final String RUBY_ORE = "mymod:ruby_ore";
    private static final String RUBY = "mymod:ruby";

    private static final String APPLY_BONUS = "\"function\":\"minecraft:apply_bonus\"";
    private static final String FORTUNE_MULTIPLIER = "\"bonusMultiplier\":2";
    private static final String MATCH_TOOL = "\"condition\":\"minecraft:match_tool\"";
    private static final String ALTERNATIVES = "\"type\":\"minecraft:alternatives\"";
    private static final String SURVIVES_EXPLOSION = "\"condition\":\"minecraft:survives_explosion\"";
    private static final String SET_COUNT = "\"function\":\"minecraft:set_count\"";

    private String blockJson;

    @BeforeEach
    void setup() {
        LootServiceImpl.BlockDropEntry drop =
            new LootServiceImpl.BlockDropEntry(RUBY_ORE, RUBY, 2, RUBY_ORE);
        blockJson = LootTableJsonBuilder.buildBlockLootTableJson(drop);
    }

    @Test
    void blockTableIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(blockJson),
            "the generated block loot table must be structurally valid JSON");
    }

    @Test
    void blockTableEmitsFortuneAndSilkTouch() {
        assertTrue(JsonAssertions.containsAll(blockJson, APPLY_BONUS, FORTUNE_MULTIPLIER, MATCH_TOOL),
            "a block drop must emit the fortune bonus function and the silk-touch tool match");
    }

    @Test
    void silkTouchUsesAlternatives() {
        assertTrue(JsonAssertions.containsAll(blockJson, ALTERNATIVES, SURVIVES_EXPLOSION),
            "silk touch must offer the silk item via alternatives and survive explosions");
    }

    @Test
    void silkItemComesBeforeRegularDrop() {
        assertTrue(blockJson.indexOf(RUBY_ORE) < blockJson.lastIndexOf(RUBY),
            "the silk-touch item must come before the regular drop");
    }

    @Test
    void simpleBlockTableIsValid() {
        String simpleJson = LootTableJsonBuilder.buildSimpleBlockLootTableJson(RUBY, 2, 5);

        assertDoesNotThrow(() -> JsonAssertions.assertValid(simpleJson),
            "the simple block loot table must be structurally valid JSON");
    }

    @Test
    void simpleBlockTableEmitsCountRange() {
        String simpleJson = LootTableJsonBuilder.buildSimpleBlockLootTableJson(RUBY, 2, 5);

        assertTrue(JsonAssertions.containsAll(simpleJson, SET_COUNT, SURVIVES_EXPLOSION, RUBY),
            "the simple block loot table must carry the count range and explosion survival");
    }

    @Test
    void singletonBlockTableIsValid() {
        String simpleJson = LootTableJsonBuilder.buildSimpleBlockLootTableJson(RUBY, 1, 1);

        assertDoesNotThrow(() -> JsonAssertions.assertValid(simpleJson),
            "the singleton block loot table must be structurally valid JSON");
    }

    @Test
    void simpleBlockTableOmitsCountForSingletons() {
        String simpleJson = LootTableJsonBuilder.buildSimpleBlockLootTableJson(RUBY, 1, 1);

        assertTrue(!simpleJson.contains(SET_COUNT),
            "a 1-1 count range must not emit a set_count function");
    }
}
