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
}
