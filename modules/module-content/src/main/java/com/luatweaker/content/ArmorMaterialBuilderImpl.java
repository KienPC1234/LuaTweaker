package com.luatweaker.content;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import java.util.HashMap;
import java.util.Map;

public class ArmorMaterialBuilderImpl implements IArmorMaterialBuilder {

    private final String id;
    private String layer;
    private String equipSound = "minecraft:item.armor.equip_diamond";
    private final Map<String, Integer> defenseMap = new HashMap<>();
    private float toughness = 3.0f;
    private float knockbackResistance = 0.1f;
    private int enchantability = 25;

    public ArmorMaterialBuilderImpl(String id) {
        this.id = id;
        this.layer = id.contains(":") ? id : "luatweaker:" + id;
        defenseMap.put("helmet", 4);
        defenseMap.put("chestplate", 9);
        defenseMap.put("leggings", 7);
        defenseMap.put("boots", 4);
    }

    @Override
    public IArmorMaterialBuilder layer(String layerPath) {
        if (layerPath != null && !layerPath.isBlank()) {
            this.layer = layerPath.contains(":") ? layerPath : "luatweaker:" + layerPath;
        }
        return this;
    }

    @Override
    public IArmorMaterialBuilder equipSound(String soundId) {
        if (soundId != null && !soundId.isBlank()) this.equipSound = soundId;
        return this;
    }

    @Override
    public IArmorMaterialBuilder defense(String slot, int defense) {
        if (slot != null) defenseMap.put(slot.toLowerCase(), defense);
        return this;
    }

    @Override
    public IArmorMaterialBuilder toughness(float toughness) {
        this.toughness = toughness;
        return this;
    }

    @Override
    public IArmorMaterialBuilder knockbackResistance(float kbRes) {
        this.knockbackResistance = kbRes;
        return this;
    }

    @Override
    public IArmorMaterialBuilder enchantability(int enchValue) {
        this.enchantability = enchValue;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public String getLayer() { return layer; }
    @Override public String getEquipSound() { return equipSound; }
    @Override public int getDefense(String slot) { return defenseMap.getOrDefault(slot.toLowerCase(), 4); }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
    @Override public int getEnchantability() { return enchantability; }
}
