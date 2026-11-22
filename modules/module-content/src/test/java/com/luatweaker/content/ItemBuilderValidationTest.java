package com.luatweaker.content;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Item builder validation: loud failures instead of silent misconfiguration. */
class ItemBuilderValidationTest {

    private static final String PICK_ID = "mymod:pick";
    private static final String THING_ID = "mymod:thing";
    private static final String GOLD_NAME = "GOLD";

    @Test
    void unknownTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ItemBuilderImpl(THING_ID).type("LASER"),
                "unknown item types must be rejected loudly");
    }

    @Test
    void unknownRarityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ItemBuilderImpl(THING_ID).rarity("MYTHIC"),
                "unknown rarities must be rejected loudly");
    }

    @Test
    void validTypeAndRarityStick() {
        ItemBuilderImpl sword = new ItemBuilderImpl("mymod:sword");
        sword.type("sword");
        ItemBuilderImpl gem = new ItemBuilderImpl("mymod:gem");
        gem.rarity("epic");

        assertAll("type and rarity must be normalized upper-case",
                () -> assertEquals("SWORD", sword.getType(), "type"),
                () -> assertEquals("EPIC", gem.getRarity(), "rarity"));
    }

    @Test
    void negativeMiningLevelThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ItemBuilderImpl(PICK_ID).miningLevel(-1),
                "negative mining levels must be rejected");
    }

    @Test
    void negativeUseDurationThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ItemBuilderImpl("mymod:bow").useDuration(-5),
                "negative use durations must be rejected");
    }

    @Test
    void goldMiningLevelKeepsVanillaStats() {
        ItemBuilderImpl builder = new ItemBuilderImpl(PICK_ID);
        builder.miningLevel(GOLD_NAME);

        assertAll("GOLD must keep vanilla gold behavior",
                () -> assertEquals(GOLD_NAME, builder.getMiningLevelName(), "tier name remembered"),
                () -> assertEquals(0, builder.getMiningLevel(), "GOLD keeps harvest level 0"));
    }

    @Test
    void numericMiningLevelClearsGold() {
        ItemBuilderImpl builder = new ItemBuilderImpl(PICK_ID);
        builder.miningLevel(GOLD_NAME);
        builder.miningLevel(3);

        assertAll("numeric tier must replace the GOLD marker",
                () -> assertTrue(builder.getMiningLevelName().isEmpty(), "marker cleared"),
                () -> assertEquals(3, builder.getMiningLevel(), "numeric tier sticks"));
    }

    @Test
    void listsAreImmutable() {
        ItemBuilderImpl builder = new ItemBuilderImpl(THING_ID);
        builder.tag("c:gems").tooltip("Shiny");

        assertAll("builder lists must be immutable copies",
                () -> assertThrows(UnsupportedOperationException.class, () -> builder.getTags().add("hack"),
                        "tag list must be immutable"),
                () -> assertThrows(UnsupportedOperationException.class, () -> builder.getTooltips().add("hack"),
                        "tooltip list must be immutable"));
    }
}
