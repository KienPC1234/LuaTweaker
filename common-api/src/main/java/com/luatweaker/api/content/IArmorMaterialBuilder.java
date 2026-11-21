package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;

@LuaDoc(description = "Builder interface for defining explicit custom Armor Materials.")
public interface IArmorMaterialBuilder {
    @LuaDoc(description = "Sets the texture layer location (e.g. 'luatweaker:ruby').", params = {"layerPath: string"}, returnType = LuaDocFragments.Builder.I_ARMOR_MATERIAL_BUILDER)
    IArmorMaterialBuilder layer(String layerPath);

    @LuaDoc(description = "Sets armor equip sound event (e.g. 'minecraft:item.armor.equip_diamond').", params = {"soundId: string"}, returnType = LuaDocFragments.Builder.I_ARMOR_MATERIAL_BUILDER)
    IArmorMaterialBuilder equipSound(String soundId);

    @LuaDoc(description = "Sets defense value for armor piece ('helmet', 'chestplate', 'leggings', 'boots').", params = {"slot: string", "defense: integer"}, returnType = LuaDocFragments.Builder.I_ARMOR_MATERIAL_BUILDER)
    IArmorMaterialBuilder defense(String slot, int defense);

    @LuaDoc(description = "Sets armor toughness value.", params = {"toughness: number"}, returnType = LuaDocFragments.Builder.I_ARMOR_MATERIAL_BUILDER)
    IArmorMaterialBuilder toughness(float toughness);

    @LuaDoc(description = "Sets knockback resistance bonus.", params = {"kbRes: number"}, returnType = LuaDocFragments.Builder.I_ARMOR_MATERIAL_BUILDER)
    IArmorMaterialBuilder knockbackResistance(float kbRes);

    @LuaDoc(description = "Sets enchantability value.", params = {"enchValue: integer"}, returnType = LuaDocFragments.Builder.I_ARMOR_MATERIAL_BUILDER)
    IArmorMaterialBuilder enchantability(int enchValue);

    String getId();
    String getLayer();
    String getEquipSound();
    int getDefense(String slot);
    float getToughness();
    float getKnockbackResistance();
    int getEnchantability();
}
