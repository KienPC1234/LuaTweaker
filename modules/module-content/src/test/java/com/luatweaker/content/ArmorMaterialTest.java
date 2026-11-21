package com.luatweaker.content;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Armor materials keep every declared stat. */
class ArmorMaterialTest extends ContentFixture {

    private static final String ARMOR_CHUNK = "armor_material";
    private static final String ARMOR_SCRIPT =
            "startup:createArmorMaterial('test_ruby', function(mat)\n" +
            "    mat:layer('luatweaker:test_ruby')\n" +
            "       :equipSound('minecraft:item.armor.equip_iron')\n" +
            "       :defense('helmet', 5)\n" +
            "       :toughness(4.5)\n" +
            "       :knockbackResistance(0.2)\n" +
            "       :enchantability(30)\n" +
            "end)";

    private IArmorMaterialBuilder armorMaterial() {
        engine.executeStringOrThrow(ARMOR_SCRIPT, ARMOR_CHUNK);
        return contentService.getArmorMaterial("test_ruby");
    }

    @Test
    void materialIsRegistered() {
        assertNotNull(armorMaterial(), "createArmorMaterial must register the definition");
    }

    @Test
    void helmetDefenseKept() {
        assertEquals(5, armorMaterial().getDefense("helmet"),
                "the helmet defense must be kept");
    }

    @Test
    void toughnessKept() {
        assertEquals(4.5f, armorMaterial().getToughness(),
                "the toughness must be kept");
    }

    @Test
    void knockbackResistanceKept() {
        assertEquals(0.2f, armorMaterial().getKnockbackResistance(),
                "the knockback resistance must be kept");
    }

    @Test
    void enchantabilityKept() {
        assertEquals(30, armorMaterial().getEnchantability(),
                "the enchantability must be kept");
    }

    @Test
    void layerKept() {
        assertEquals("luatweaker:test_ruby", armorMaterial().getLayer(),
                "the armor layer must be kept");
    }

    @Test
    void equipSoundKept() {
        assertEquals("minecraft:item.armor.equip_iron", armorMaterial().getEquipSound(),
                "the equip sound must be kept");
    }
}
