package com.luatweaker.api.wrapper;

import com.luatweaker.api.annotation.LuaDoc;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LuaDoc(description = "Represents an item stack definition with count, damage, custom name, lore, enchantments, and NBT.")
public record ItemCount(
    String itemId,
    int count,
    int damage,
    String customName,
    List<String> lore,
    Map<String, Integer> enchantments,
    String nbtJson
) {
    public ItemCount {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId cannot be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        if (lore == null) lore = Collections.emptyList();
        if (enchantments == null) enchantments = Collections.emptyMap();
    }

    public ItemCount(String itemId, int count) {
        this(itemId, count, 0, null, Collections.emptyList(), Collections.emptyMap(), null);
    }

    public ItemCount withCount(int newCount) {
        return new ItemCount(itemId, newCount, damage, customName, lore, enchantments, nbtJson);
    }

    public ItemCount withDamage(int newDamage) {
        return new ItemCount(itemId, count, newDamage, customName, lore, enchantments, nbtJson);
    }

    public ItemCount withName(String name) {
        return new ItemCount(itemId, count, damage, name, lore, enchantments, nbtJson);
    }

    public ItemCount withLore(List<String> newLore) {
        return new ItemCount(itemId, count, damage, customName, newLore, enchantments, nbtJson);
    }

    public ItemCount withEnchantment(String enchantmentId, int level) {
        Map<String, Integer> newEnch = new HashMap<>(enchantments);
        newEnch.put(enchantmentId, level);
        return new ItemCount(itemId, count, damage, customName, lore, newEnch, nbtJson);
    }

    public ItemCount withNbt(String nbt) {
        return new ItemCount(itemId, count, damage, customName, lore, enchantments, nbt);
    }
}
