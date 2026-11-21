package com.luatweaker.api.interaction;

import org.jetbrains.annotations.NotNull;

/**
 * Enchantment primitives for an item stack. Kept separate from {@link IInteractableItem} so the
 * core item contract stays focused; {@code LuaBinder} still exposes them on the same item table.
 */
public interface IEnchantableItem {

    @NotNull
    default java.util.Map<String, Integer> getEnchantments() {
        return java.util.Map.of();
    }

    default int getEnchantmentLevel(@NotNull String enchantmentId) { return 0; }

    default boolean applyEnchantment(@NotNull String enchantmentId, int level) { return false; }

    default boolean removeEnchantment(@NotNull String enchantmentId) { return false; }
}
