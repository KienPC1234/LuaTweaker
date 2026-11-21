package com.luatweaker.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Pure serializer for the 1.21.1 data-driven enchantment format.
 *
 * <p>All validation happens here, so a malformed builder never reaches the virtual datapack.
 * The field names mirror vanilla ({@code supported_items}, {@code min_cost}, ...).</p>
 */
final class EnchantmentJson {

    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 255;
    static final Set<String> ALLOWED_SLOTS = Set.of(
            "any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body");

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private EnchantmentJson() {
    }

    record Cost(int base, int perLevelAboveFirst) {
        Cost {
            if (base < 0) {
                throw new IllegalArgumentException("Enchantment cost base must be >= 0");
            }
            if (perLevelAboveFirst < 0) {
                throw new IllegalArgumentException(
                        "Enchantment cost perLevelAboveFirst must be >= 0");
            }
        }
    }

    record Spec(@NotNull String namespace, @NotNull String path,
                @Nullable String descriptionText, @Nullable String descriptionKey,
                @NotNull String supportedItems, @Nullable String primaryItems,
                int weight, int maxLevel, @NotNull Cost minCost, @NotNull Cost maxCost,
                int anvilCost, @NotNull List<String> slots, @Nullable String exclusiveSet,
                @NotNull JsonObject effects) {
    }

    static @NotNull String render(@NotNull Spec spec) {
        validate(spec);
        JsonObject root = new JsonObject();
        root.add("description", description(spec));
        root.addProperty("supported_items", spec.supportedItems());
        if (spec.primaryItems() != null) {
            root.addProperty("primary_items", spec.primaryItems());
        }
        root.addProperty("weight", spec.weight());
        root.addProperty("max_level", spec.maxLevel());
        root.add("min_cost", cost(spec.minCost()));
        root.add("max_cost", cost(spec.maxCost()));
        root.addProperty("anvil_cost", spec.anvilCost());
        root.add("slots", slots(spec.slots()));
        if (spec.exclusiveSet() != null) {
            root.addProperty("exclusive_set", spec.exclusiveSet());
        }
        root.add("effects", spec.effects().deepCopy());
        return PRETTY.toJson(root);
    }

    static @NotNull String defaultDescriptionKey(@NotNull String namespace, @NotNull String path) {
        return "enchantment." + namespace + "." + path;
    }

    private static JsonObject description(Spec spec) {
        JsonObject description = new JsonObject();
        if (spec.descriptionKey() != null) {
            description.addProperty("translate", spec.descriptionKey());
        } else {
            description.addProperty("text", spec.descriptionText());
        }
        return description;
    }

    private static JsonObject cost(Cost cost) {
        JsonObject json = new JsonObject();
        json.addProperty("base", cost.base());
        json.addProperty("per_level_above_first", cost.perLevelAboveFirst());
        return json;
    }

    private static JsonArray slots(List<String> slots) {
        JsonArray array = new JsonArray();
        for (String slot : slots) {
            array.add(slot.toLowerCase(java.util.Locale.ROOT));
        }
        return array;
    }

    private static void validate(Spec spec) {
        requireSegment(spec.namespace(), "namespace");
        requireSegment(spec.path(), "path");
        validateDescription(spec);
        validateNumbers(spec);
        validateSlots(spec.slots());
        if (spec.effects() == null) {
            throw new IllegalArgumentException("Enchantment effects must be a JSON object");
        }
    }

    private static void validateDescription(Spec spec) {
        if (spec.supportedItems().isBlank()) {
            throw new IllegalArgumentException("Enchantment supportedItems must not be blank");
        }
        if (spec.descriptionKey() != null) {
            return;
        }
        if (spec.descriptionText() == null || spec.descriptionText().isBlank()) {
            throw new IllegalArgumentException(
                    "Enchantment description must be a non-blank text or a translation key");
        }
    }

    private static void validateNumbers(Spec spec) {
        if (spec.weight() <= 0) {
            throw new IllegalArgumentException("Enchantment weight must be > 0");
        }
        if (spec.maxLevel() < MIN_LEVEL || spec.maxLevel() > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Enchantment maxLevel must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
        }
        if (spec.anvilCost() < 0) {
            throw new IllegalArgumentException("Enchantment anvilCost must be >= 0");
        }
    }

    private static void validateSlots(List<String> slots) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Enchantment needs at least one slot group");
        }
        for (String slot : slots) {
            if (!ALLOWED_SLOTS.contains(slot.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Unknown enchantment slot '" + slot + "'; expected one of " + ALLOWED_SLOTS);
            }
        }
    }

    private static void requireSegment(String value, String label) {
        if (value == null || !value.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(
                    "Enchantment " + label + " is not a valid resource id segment: '" + value + "'");
        }
    }
}
