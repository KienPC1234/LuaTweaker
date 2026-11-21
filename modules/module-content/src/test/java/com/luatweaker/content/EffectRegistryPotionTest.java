package com.luatweaker.content;

import com.luatweaker.api.content.PotionDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pins the validation of custom potions. */
class EffectRegistryPotionTest {

    private static final String BAD_ID = "bad";
    private static final String SPEED = "minecraft:speed";

    @AfterEach
    void clearRegistry() {
        EffectRegistry.clear();
    }

    @Test
    void potionStoresEveryEffect() {
        EffectRegistry.registerPotion(new PotionDefinition("my_potion", List.of(
                new PotionDefinition.PotionEffectSpec(SPEED, 200, 1),
                new PotionDefinition.PotionEffectSpec("luatweaker:my_effect", -1, 0))));

        PotionDefinition stored = EffectRegistry.getPotions().get("luatweaker:my_potion");
        assertEquals(2, stored.effects().size(), "both effect instances must be stored");
    }

    @Test
    void potionWithoutEffectsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerPotion(new PotionDefinition("empty", List.of())),
                "a potion with no effects must be rejected");
    }

    @Test
    void potionAmplifierAboveTheCapIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerPotion(new PotionDefinition(BAD_ID, List.of(
                        new PotionDefinition.PotionEffectSpec(SPEED, 200, 256)))),
                "an amplifier above 255 must be rejected");
    }

    @Test
    void potionDurationBelowInfiniteIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerPotion(new PotionDefinition(BAD_ID, List.of(
                        new PotionDefinition.PotionEffectSpec(SPEED, -2, 0)))),
                "a duration below -1 must be rejected");
    }
}
