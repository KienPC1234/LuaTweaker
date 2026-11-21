package com.luatweaker.content;

import com.luatweaker.api.content.MobEffectDefinition;
import com.luatweaker.api.content.PotionDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Drives the Lua effect/potion builders through a real engine. */
class ContentEffectBindingTest extends ContentFixture {

    @AfterEach
    void clearRegistry() {
        EffectRegistry.clear();
    }

    @Test
    void callbackStyleRegistersAMobEffect() {
        engine.executeStringOrThrow("""
                Content:createMobEffect('mymod:ruby_speed', function(e)
                    e:color(0xFF0000)
                     :category('BENEFICIAL')
                     :attribute('minecraft:generic.movement_speed', 0.4, 'ADD_MULTIPLIED_TOTAL')
                end)
                """, "MobEffectCallback");

        assertRubySpeed();
    }

    @Test
    void chainableMobEffectNeedsRegister() {
        engine.executeStringOrThrow("""
                local e = Content.NewMobEffect('mymod:unregistered')
                e:category('HARMFUL')
                """, "MobEffectUnregistered");

        assertFalse(EffectRegistry.getMobEffects().containsKey("mymod:unregistered"),
                "NewMobEffect must not register until Register() is called");
    }

    @Test
    void attributeDefaultsToAddValue() {
        engine.executeStringOrThrow("""
                Content.NewMobEffect('mymod:armor_bonus')
                    :attribute('minecraft:generic.armor', 2)
                    :Register()
                """, "MobEffectDefaultOperation");

        MobEffectDefinition definition = EffectRegistry.getMobEffects().get("mymod:armor_bonus");
        assertEquals("ADD_VALUE", definition.attributeModifiers().get(0).operation(),
                "the operation must default to ADD_VALUE");
    }

    @Test
    void chainablePotionRegistersEveryEffect() {
        engine.executeStringOrThrow("""
                Content.NewPotion('mymod:ruby_swiftness')
                    :effect('minecraft:speed', 3600, 1)
                    :effect('minecraft:jump_boost', 1200, 0)
                    :Register()
                """, "PotionChain");

        assertSwiftness();
    }

    @Test
    void invalidDefinitionFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow("""
                        Content.NewMobEffect('mymod:bad')
                            :category('NOT_A_CATEGORY')
                            :Register()
                        """, "MobEffectBadCategory"),
                "an invalid category must raise through the binding");
    }

    @Test
    void customTextureAndPropertiesArePreserved() {
        engine.executeStringOrThrow("""
                Content.NewMobEffect('mymod:frost_aura')
                    :beneficial()
                    :texture('mymod:textures/mob_effect/frost.png')
                    :displayName('Frost Aura')
                    :tickInterval(20)
                    :curable(false)
                    :instant()
                    :Register()
                """, "MobEffectCustomProps");

        MobEffectDefinition def = EffectRegistry.getMobEffects().get("mymod:frost_aura");
        org.junit.jupiter.api.Assertions.assertAll("custom props",
                () -> assertEquals("BENEFICIAL", def.category(), "category must be BENEFICIAL"),
                () -> assertEquals("mymod:textures/mob_effect/frost.png", def.texture(), "texture must be preserved"),
                () -> assertEquals("Frost Aura", def.displayName(), "displayName must be preserved"),
                () -> assertEquals(20, def.tickInterval(), "tickInterval must be preserved"),
                () -> assertFalse(def.curable(), "curable must be false"),
                () -> assertEquals(true, def.instantaneous(), "instantaneous must be true")
        );
    }

    private void assertRubySpeed() {
        MobEffectDefinition definition = EffectRegistry.getMobEffects().get("mymod:ruby_speed");
        assertEquals("BENEFICIAL", definition.category(), "the category must be forwarded");
        assertEquals(0xFF0000, definition.color(), "the color must be forwarded");
        assertEquals(1, definition.attributeModifiers().size(),
                "the attribute modifier must be forwarded");
        assertEquals("ADD_MULTIPLIED_TOTAL",
                definition.attributeModifiers().get(0).operation(),
                "the operation must be forwarded");
    }

    private void assertSwiftness() {
        PotionDefinition definition = EffectRegistry.getPotions().get("mymod:ruby_swiftness");
        assertEquals(2, definition.effects().size(), "both effects must be forwarded");
        assertEquals(3600, definition.effects().get(0).durationTicks(),
                "the duration must be forwarded");
    }
}
