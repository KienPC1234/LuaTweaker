package com.luatweaker.content;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArmorMaterialBuilderImpl implements IArmorMaterialBuilder {

    private final String id;
    private String layerPath;
    private String equipSoundId = "minecraft:item.armor.equip_diamond";
    private final Map<String, Integer> defenseMap = new ConcurrentHashMap<>();
    private float toughnessValue = 3.0f;
    private float knockbackResistanceValue = 0.1f;
    private int enchantabilityValue = 25;

    public ArmorMaterialBuilderImpl(String id) {
        this.id = id;
        this.layerPath = id.contains(":") ? id : "luatweaker:" + id;
        defenseMap.put("helmet", 4);
        defenseMap.put("chestplate", 9);
        defenseMap.put("leggings", 7);
        defenseMap.put("boots", 4);
    }

    @Override
    public IArmorMaterialBuilder layer(String layerPath) {
        if (layerPath != null && !layerPath.isBlank()) {
            this.layerPath = layerPath.contains(":") ? layerPath : "luatweaker:" + layerPath;
        }
        return this;
    }

    @Override
    public IArmorMaterialBuilder equipSound(String soundId) {
        if (soundId != null && !soundId.isBlank()) {
            this.equipSoundId = soundId;
        }
        return this;
    }

    @Override
    public IArmorMaterialBuilder defense(String slot, int defense) {
        if (slot != null) {
            defenseMap.put(slot.toLowerCase(Locale.ROOT), defense);
        }
        return this;
    }

    @Override
    public IArmorMaterialBuilder toughness(float toughness) {
        this.toughnessValue = toughness;
        return this;
    }

    @Override
    public IArmorMaterialBuilder knockbackResistance(float kbRes) {
        this.knockbackResistanceValue = kbRes;
        return this;
    }

    @Override
    public IArmorMaterialBuilder enchantability(int enchValue) {
        this.enchantabilityValue = enchValue;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public String getLayer() { return layerPath; }
    @Override public String getEquipSound() { return equipSoundId; }
    @Override public int getDefense(String slot) { return defenseMap.getOrDefault(slot.toLowerCase(Locale.ROOT), 4); }
    @Override public float getToughness() { return toughnessValue; }
    @Override public float getKnockbackResistance() { return knockbackResistanceValue; }
    @Override public int getEnchantability() { return enchantabilityValue; }
}
