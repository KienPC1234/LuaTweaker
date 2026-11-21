package com.luatweaker.content;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Mutable data holder for one enchantment definition. Kept method-free on purpose: the Lua
 * binding mutates these fields, and {@link EnchantmentJson} owns all validation/rendering.
 */
final class EnchantmentConfig {

    private static final int DEFAULT_WEIGHT = 10;
    private static final int DEFAULT_MAX_LEVEL = 1;
    private static final int DEFAULT_COST_BASE = 1;
    private static final int DEFAULT_COST_PER_LEVEL = 11;
    private static final int DEFAULT_MAX_COST_BASE = 21;
    private static final int DEFAULT_ANVIL_COST = 1;
    private static final String DEFAULT_SLOT = "MAINHAND";

    final String namespace;
    final String path;
    String descriptionText = "";
    String descriptionKey = "";
    boolean usesTranslation;
    String supportedItems = "";
    String primaryItems;
    int weight = DEFAULT_WEIGHT;
    int maxLevel = DEFAULT_MAX_LEVEL;
    EnchantmentJson.Cost minCost =
            new EnchantmentJson.Cost(DEFAULT_COST_BASE, DEFAULT_COST_PER_LEVEL);
    EnchantmentJson.Cost maxCost =
            new EnchantmentJson.Cost(DEFAULT_MAX_COST_BASE, DEFAULT_COST_PER_LEVEL);
    int anvilCost = DEFAULT_ANVIL_COST;
    List<String> slots = List.of(DEFAULT_SLOT);
    String exclusiveSet;
    boolean curse;
    JsonObject effects = new JsonObject();

    EnchantmentConfig(@NotNull String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Enchantment id must not be blank");
        }
        String[] parts = parseId(id);
        this.namespace = parts[0];
        this.path = parts[1];
    }

    EnchantmentJson.Spec toSpec() {
        return new EnchantmentJson.Spec(namespace, path,
                usesTranslation ? null : descriptionText,
                usesTranslation ? descriptionKey : null,
                supportedItems, primaryItems, weight, maxLevel, minCost, maxCost, anvilCost,
                normalizeSlots(), exclusiveSet, effects);
    }

    private List<String> normalizeSlots() {
        if (slots == null) {
            return List.of();
        }
        return slots.stream().map(slot -> slot.toLowerCase(Locale.ROOT)).toList();
    }

    private static String[] parseId(String raw) {
        int colon = raw.indexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            return new String[] { raw.substring(0, colon), raw.substring(colon + 1) };
        }
        return new String[] { "luatweaker", raw };
    }
}
