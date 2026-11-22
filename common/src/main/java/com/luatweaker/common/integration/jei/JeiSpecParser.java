package com.luatweaker.common.integration.jei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses the raw Lua spec tables handed to {@link CustomJeiRegistry} into the immutable
 * records JEI consumes.
 *
 * <p>A spec without a usable id yields {@code null} instead of a half-built entry, so the
 * registry keeps its old behaviour of ignoring malformed registrations.</p>
 */
final class JeiSpecParser {

    private static final String DEFAULT_NAMESPACE = "luatweaker";
    private static final int DEFAULT_CATEGORY_WIDTH = 150;
    private static final int DEFAULT_CATEGORY_HEIGHT = 60;
    private static final int DEFAULT_COOK_TIME = 100;
    /** Sentinel for "this widget is not drawn" (arrow / flame positions). */
    private static final int NO_WIDGET_POSITION = -1;

    private JeiSpecParser() {}

    @Nullable
    static CustomJeiRegistry.CustomCategorySpec parseCategory(@Nullable Map<String, Object> spec) {
        if (spec == null) { return null; }
        String rawId = (String) spec.get("id");
        if (rawId == null || rawId.isBlank()) { return null; }

        String titleStr = (String) spec.getOrDefault("title", rawId);
        return new CustomJeiRegistry.CustomCategorySpec(
                resolveUid(rawId),
                Component.literal(titleStr),
                (String) spec.getOrDefault("icon", "minecraft:furnace"),
                (String) spec.get("bg"),
                intOr(spec.get("width"), DEFAULT_CATEGORY_WIDTH),
                intOr(spec.get("height"), DEFAULT_CATEGORY_HEIGHT),
                extractCatalysts(spec.get("catalysts")),
                extractSlots(spec.get("slots")),
                intOr(spec.get("arrowX"), NO_WIDGET_POSITION),
                intOr(spec.get("arrowY"), NO_WIDGET_POSITION),
                intOr(spec.get("flameX"), NO_WIDGET_POSITION),
                intOr(spec.get("flameY"), NO_WIDGET_POSITION));
    }

    @Nullable
    static CustomJeiRegistry.CustomRecipeEntry parseRecipe(@Nullable String categoryId,
                                                           @Nullable Map<String, Object> recipe) {
        if (categoryId == null || recipe == null) { return null; }
        return new CustomJeiRegistry.CustomRecipeEntry(
                resolveUid(categoryId),
                extractStacks(recipe.get("inputs")),
                extractStacks(recipe.get("outputs")),
                floatOr(recipe.get("xp"), 0.0f),
                intOr(recipe.get("cookTime"), DEFAULT_COOK_TIME));
    }

    /** Category / recipe ids without a namespace belong to LuaTweaker. */
    private static ResourceLocation resolveUid(String rawId) {
        String fullId = rawId.contains(":") ? rawId : (DEFAULT_NAMESPACE + ":" + rawId);
        return ResourceLocation.parse(fullId);
    }

    private static int intOr(@Nullable Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static float floatOr(@Nullable Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static List<String> extractCatalysts(@Nullable Object raw) {
        List<String> catalysts = new ArrayList<>();
        if (!(raw instanceof List<?> list)) { return catalysts; }
        for (Object obj : list) {
            if (obj instanceof String catalyst) { catalysts.add(catalyst); }
        }
        return catalysts;
    }

    private static List<CustomJeiRegistry.JeiSlotSpec> extractSlots(@Nullable Object raw) {
        List<CustomJeiRegistry.JeiSlotSpec> slots = new ArrayList<>();
        if (!(raw instanceof List<?> list)) { return slots; }
        for (Object obj : list) {
            if (obj instanceof Map<?, ?> slot) { slots.add(slotFromMap(slot)); }
        }
        return slots;
    }

    private static CustomJeiRegistry.JeiSlotSpec slotFromMap(Map<?, ?> slot) {
        boolean isInput = !Boolean.FALSE.equals(slot.get("isInput"));
        return new CustomJeiRegistry.JeiSlotSpec(
                intOr(slot.get("x"), 0),
                intOr(slot.get("y"), 0),
                isInput);
    }

    /** Resolves item ids into stacks; unknown or malformed ids are skipped. */
    private static List<ItemStack> extractStacks(@Nullable Object raw) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!(raw instanceof List<?> list)) { return stacks; }
        for (Object obj : list) {
            addStack(stacks, obj);
        }
        return stacks;
    }

    private static void addStack(List<ItemStack> stacks, Object rawId) {
        if (!(rawId instanceof String idStr)) { return; }
        ResourceLocation rl = ResourceLocation.tryParse(idStr);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) { return; }
        stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(rl)));
    }
}
