package com.luatweaker.platform.integration.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry holding custom JEI recipe categories and recipe entries registered via Lua.
 *
 * <p>Categories and recipes keep their Lua registration order: JEI lists them in the order
 * the scripts declared them, so the maps/lists below are deliberately ordered.</p>
 */
public final class CustomJeiRegistry {

    public record JeiSlotSpec(int x, int y, boolean isInput) {}

    public record CustomCategorySpec(
            ResourceLocation uid,
            Component title,
            String iconItemId,
            String backgroundTexture,
            int width,
            int height,
            List<String> catalysts,
            List<JeiSlotSpec> slots,
            int arrowX,
            int arrowY,
            int flameX,
            int flameY
    ) {}

    public record CustomRecipeEntry(
            ResourceLocation categoryUid,
            List<ItemStack> inputs,
            List<ItemStack> outputs,
            float xp,
            int cookTime
    ) {}

    /**
     * Insertion-ordered on purpose: {@link #getCategories()} is iterated to register JEI
     * categories, so the Lua declaration order is the order players see. A ConcurrentHashMap
     * would silently shuffle it, which is why this map stays a {@link LinkedHashMap}.
     */
    private static final Map<ResourceLocation, CustomCategorySpec> CATEGORIES = new LinkedHashMap<>();
    private static final List<CustomRecipeEntry> RECIPES = new ArrayList<>();

    /** Guards both collections: Lua registration and the JEI client threads both touch them. */
    private static final ReentrantLock REGISTRY_LOCK = new ReentrantLock();

    private CustomJeiRegistry() {}

    public static void registerCategory(CustomCategorySpec spec) {
        if (spec == null) { return; }
        REGISTRY_LOCK.lock();
        try {
            CATEGORIES.put(spec.uid(), spec);
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }

    public static void addRecipe(CustomRecipeEntry entry) {
        if (entry == null) { return; }
        REGISTRY_LOCK.lock();
        try {
            RECIPES.add(entry);
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }

    public static void registerCategoryFromSpec(Map<String, Object> spec) {
        CustomCategorySpec parsed = JeiSpecParser.parseCategory(spec);
        if (parsed != null) {
            registerCategory(parsed);
        }
    }

    public static void registerRecipeFromSpec(String categoryId, Map<String, Object> recipe) {
        CustomRecipeEntry parsed = JeiSpecParser.parseRecipe(categoryId, recipe);
        if (parsed != null) {
            addRecipe(parsed);
        }
    }

    public static Collection<CustomCategorySpec> getCategories() {
        REGISTRY_LOCK.lock();
        try {
            return Collections.unmodifiableCollection(CATEGORIES.values());
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }

    public static List<CustomRecipeEntry> getRecipesForCategory(ResourceLocation categoryUid) {
        REGISTRY_LOCK.lock();
        try {
            List<CustomRecipeEntry> list = new ArrayList<>();
            for (CustomRecipeEntry recipe : RECIPES) {
                if (recipe.categoryUid().equals(categoryUid)) {
                    list.add(recipe);
                }
            }
            return list;
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }

    public static void clear() {
        REGISTRY_LOCK.lock();
        try {
            CATEGORIES.clear();
            RECIPES.clear();
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }
}
