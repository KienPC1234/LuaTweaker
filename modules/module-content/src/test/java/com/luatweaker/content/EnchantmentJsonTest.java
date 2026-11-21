package com.luatweaker.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the vanilla 1.21.1 enchantment JSON shape and every validation rule. */
class EnchantmentJsonTest {

    private static final String SWORD_TAG = "#minecraft:enchantable/sword";

    @Test
    void rendersTheVanillaFields() {
        JsonObject effects = JsonParser.parseString(
                "{\"minecraft:damage\":[{\"effect\":{\"type\":\"minecraft:add\",\"value\":1.0}}]}")
                .getAsJsonObject();
        EnchantmentJson.Spec spec = new EnchantmentJson.Spec(
                "mymod", "ruby_fortune", "Ruby Fortune", null,
                SWORD_TAG, SWORD_TAG,
                5, 3, new EnchantmentJson.Cost(1, 10), new EnchantmentJson.Cost(21, 10),
                2, List.of("MAINHAND"), "#minecraft:exclusive_set/damage", effects);

        JsonObject json = JsonParser.parseString(EnchantmentJson.render(spec)).getAsJsonObject();

        assertVanillaFields(json);
    }

    @Test
    void translationDescriptionIsEmitted() {
        EnchantmentJson.Spec spec = baseSpec()
                .withDescription(null, "enchantment.mymod.ruby_fortune").build();

        JsonObject json = JsonParser.parseString(EnchantmentJson.render(spec)).getAsJsonObject();

        assertEquals("enchantment.mymod.ruby_fortune",
                json.getAsJsonObject("description").get("translate").getAsString(),
                "a translation key must be emitted as a translate component");
    }

    @Test
    void invalidWeightIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnchantmentJson.render(baseSpec().withWeight(0).build()),
                "weight 0 would make the enchantment unselectable");
    }

    @Test
    void maxLevelBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnchantmentJson.render(baseSpec().withMaxLevel(0).build()),
                "maxLevel below 1 is invalid");
    }

    @Test
    void maxLevelAboveTheVanillaCapIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnchantmentJson.render(baseSpec().withMaxLevel(256).build()),
                "maxLevel above the vanilla cap is invalid");
    }

    @Test
    void invalidSlotIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnchantmentJson.render(baseSpec().withSlots(List.of("POCKET")).build()),
                "an unknown equipment slot group must be rejected");
    }

    @Test
    void blankSupportedItemsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnchantmentJson.render(baseSpec().withSupportedItems("  ").build()),
                "an enchantment without supported items cannot be loaded");
    }

    private static void assertVanillaFields(JsonObject json) {
        assertEquals("Ruby Fortune", json.getAsJsonObject("description").get("text").getAsString(),
                "a literal description must be emitted as a text component");
        assertEquals(SWORD_TAG, json.get("supported_items").getAsString(),
                "tags must keep their # prefix");
        assertEquals(5, json.get("weight").getAsInt(), "weight must be emitted");
        assertEquals(3, json.get("max_level").getAsInt(), "max_level must be emitted");
        assertEquals(1, json.getAsJsonObject("min_cost").get("base").getAsInt(),
                "min_cost.base must be emitted");
        assertEquals(10, json.getAsJsonObject("max_cost").get("per_level_above_first").getAsInt(),
                "max_cost.per_level_above_first must be emitted");
        assertEquals("#minecraft:exclusive_set/damage", json.get("exclusive_set").getAsString(),
                "exclusive_set must be emitted");
        assertTrue(json.getAsJsonObject("effects").has("minecraft:damage"),
                "the effects object must be preserved");
    }

    private static SpecBuilder baseSpec() {
        return new SpecBuilder();
    }

    /** Small named test builder so each invalid case changes exactly one field. */
    private static final class SpecBuilder {
        private static final String NAMESPACE = "mymod";
        private static final String PATH = "ruby_fortune";
        private static final String PRIMARY_ITEMS = SWORD_TAG;
        private static final EnchantmentJson.Cost MIN_COST = new EnchantmentJson.Cost(1, 10);
        private static final EnchantmentJson.Cost MAX_COST = new EnchantmentJson.Cost(21, 10);
        private static final int ANVIL_COST = 2;
        private String descriptionText = "Ruby Fortune";
        private String descriptionKey;
        private String supportedItems = SWORD_TAG;
        private int weight = 5;
        private int maxLevel = 3;
        private List<String> slots = List.of("MAINHAND");
        private String exclusiveSet;

        SpecBuilder withWeight(int value) {
            this.weight = value;
            return this;
        }

        SpecBuilder withMaxLevel(int value) {
            this.maxLevel = value;
            return this;
        }

        SpecBuilder withSlots(List<String> value) {
            this.slots = value;
            return this;
        }

        SpecBuilder withSupportedItems(String value) {
            this.supportedItems = value;
            return this;
        }

        SpecBuilder withDescription(String text, String key) {
            this.descriptionText = text;
            this.descriptionKey = key;
            return this;
        }

        EnchantmentJson.Spec build() {
            return new EnchantmentJson.Spec(NAMESPACE, PATH, descriptionText, descriptionKey,
                    supportedItems, PRIMARY_ITEMS, weight, maxLevel, MIN_COST, MAX_COST, ANVIL_COST,
                    slots, exclusiveSet, new JsonObject());
        }
    }
}
