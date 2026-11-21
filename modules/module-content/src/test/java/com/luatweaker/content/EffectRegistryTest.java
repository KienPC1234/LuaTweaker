package com.luatweaker.content;

import com.luatweaker.api.content.MobEffectDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the validation and canonical ids of custom mob effects. */
class EffectRegistryTest {

    private static final String BAD_ID = "bad";
    private static final String SPEED = "minecraft:generic.movement_speed";

    @AfterEach
    void clearRegistry() {
        EffectRegistry.clear();
    }

    @Test
    void mobEffectIsStoredWithCanonicalId() {
        EffectRegistry.registerMobEffect(new MobEffectDefinition(
                "my_effect", 0x00FF00, "beneficial",
                List.of(new MobEffectDefinition.AttributeModifierSpec(
                        SPEED, 0.2, "add_multiplied_total"))));

        assertCanonicalMobEffect();
    }

    @Test
    void invalidColorIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerMobEffect(
                        new MobEffectDefinition(BAD_ID, 0x1FFFFFF, "NEUTRAL", List.of())),
                "a color above 0xFFFFFF must be rejected");
    }

    @Test
    void invalidCategoryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerMobEffect(
                        new MobEffectDefinition(BAD_ID, 0, "AWESOME", List.of())),
                "an unknown category must be rejected");
    }

    @Test
    void invalidAttributeOperationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerMobEffect(new MobEffectDefinition(
                        BAD_ID, 0, "NEUTRAL",
                        List.of(new MobEffectDefinition.AttributeModifierSpec(
                                SPEED, 1, "MULTIPLY")))),
                "an unknown attribute operation must be rejected");
    }

    @Test
    void invalidResourceIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EffectRegistry.registerMobEffect(
                        new MobEffectDefinition("Not A Valid Id", 0, "NEUTRAL", List.of())),
                "a malformed resource id must be rejected");
    }

    private void assertCanonicalMobEffect() {
        assertTrue(EffectRegistry.getMobEffects().containsKey("luatweaker:my_effect"),
                "a namespace-less id must default to luatweaker");
        MobEffectDefinition stored = EffectRegistry.getMobEffects().get("luatweaker:my_effect");
        assertEquals("BENEFICIAL", stored.category(), "the category must be normalized");
    }
}
