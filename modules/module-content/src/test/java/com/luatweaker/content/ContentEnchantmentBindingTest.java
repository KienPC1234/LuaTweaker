package com.luatweaker.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the Lua enchantment builder writes valid data into the virtual datapack. */
class ContentEnchantmentBindingTest extends ContentFixture {

    @Test
    void callbackStyleWritesTheEnchantmentJson() {
        engine.executeStringOrThrow("""
                startup:createEnchantment('mymod:ruby_fortune', function(e)
                    e:descriptionTranslate('enchantment.mymod.ruby_fortune')
                     :supportedItems('#minecraft:enchantable/sword')
                     :primaryItems('#minecraft:enchantable/sword')
                     :weight(5)
                     :maxLevel(3)
                     :minCost(1, 10)
                     :maxCost(21, 10)
                     :anvilCost(2)
                     :slots({ 'MAINHAND' })
                     :effects([[{"minecraft:damage":[{"effect":{"type":"minecraft:add","value":1.0}}]}]])
                end)
                """, "EnchantmentCallback");

        assertRubyFortune();
    }

    @Test
    void chainableStyleRegistersThroughRegister() {
        engine.executeStringOrThrow("""
                local e = Content.NewEnchantment('mymod:ice_aspect')
                e:description('Ice Aspect')
                 :supportedItems('minecraft:iron_sword')
                 :slots({ 'MAINHAND', 'OFFHAND' })
                 :Register()
                """, "EnchantmentChain");

        assertIceAspect();
    }

    @Test
    void missingSupportedItemsFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow("""
                        Content:createEnchantment('mymod:broken', function(e)
                            e:maxLevel(2)
                        end)
                        """, "EnchantmentMissingItems"),
                "an enchantment without supported items must fail instead of writing invalid data");
    }

    @Test
    void invalidEffectsJsonFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow("""
                        Content.NewEnchantment('mymod:broken_effects')
                            :supportedItems('minecraft:iron_sword')
                            :effects('{ not json')
                            :Register()
                        """, "EnchantmentBadEffects"),
                "invalid effects JSON must fail instead of writing invalid data");
    }

    @Test
    void luaTableEffectsAndHelpersAreSerialized() {
        engine.executeStringOrThrow("""
                Content.NewEnchantment('mymod:vampiric')
                    :description('Vampiric')
                    :supportedItems('#minecraft:enchantable/sword')
                    :damage(3.0, 1.5)
                    :knockback(1.0)
                    :curse(true)
                    :effects({
                        ['minecraft:post_attack'] = {
                            {
                                enchanted = 'attacker',
                                affected = 'victim'
                            }
                        }
                    })
                    :Register()
                """, "EnchantmentTableAndHelpers");

        String written = datapackService.getVirtualFiles()
                .get("data/mymod/enchantment/vampiric.json");
        org.junit.jupiter.api.Assertions.assertAll("enchantment effects",
                () -> assertNotNull(written, "enchantment json must exist"),
                () -> {
                    JsonObject json = JsonParser.parseString(written != null ? written : "{}").getAsJsonObject();
                    JsonObject effects = json.getAsJsonObject("effects");
                    assertTrue(effects.has("minecraft:damage"), "damage helper must inject damage effect");
                    assertTrue(effects.has("minecraft:knockback"), "knockback helper must inject knockback effect");
                    assertTrue(effects.has("minecraft:post_attack"), "table effects must be serialized");
                }
        );
    }

    private void assertRubyFortune() {
        String written = datapackService.getVirtualFiles()
                .get("data/mymod/enchantment/ruby_fortune.json");
        assertNotNull(written, "createEnchantment must write the canonical datapack path");
        JsonObject json = JsonParser.parseString(written).getAsJsonObject();
        assertEquals("enchantment.mymod.ruby_fortune",
                json.getAsJsonObject("description").get("translate").getAsString(),
                "the translation key must be forwarded");
        assertEquals(3, json.get("max_level").getAsInt(), "max_level must be forwarded");
        assertTrue(json.getAsJsonObject("effects").has("minecraft:damage"),
                "the effects object must be forwarded");
    }

    private void assertIceAspect() {
        String written = datapackService.getVirtualFiles()
                .get("data/mymod/enchantment/ice_aspect.json");
        assertNotNull(written, "NewEnchantment + Register must write the datapack file");
        JsonObject json = JsonParser.parseString(written).getAsJsonObject();
        assertEquals("Ice Aspect",
                json.getAsJsonObject("description").get("text").getAsString(),
                "a literal description must be forwarded");
        assertEquals(2, json.getAsJsonArray("slots").size(), "both slot groups must be forwarded");
        assertEquals("mainhand", json.getAsJsonArray("slots").get(0).getAsString(),
                "slot groups must use the lowercase vanilla serialized name");
    }
}
