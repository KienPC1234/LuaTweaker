package com.luatweaker.common.interaction;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchantment read/write for one item stack. Extracted from the item facade so the facade keeps
 * its interface shape while this collaborator owns registry lookups and mutation rules.
 */
final class ItemEnchantmentOps {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    private final Object owner;

    ItemEnchantmentOps(@Nullable Object owner) {
        this.owner = owner;
    }

    @NotNull Map<String, Integer> all(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return Map.of();
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        Map<String, Integer> result = new ConcurrentHashMap<>();
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            String id = holder.unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse(null);
            if (id != null) {
                result.put(id, enchantments.getLevel(holder));
            }
        }
        return result;
    }

    int level(@NotNull ItemStack stack, @NotNull String enchantmentId) {
        if (stack.isEmpty()) {
            return 0;
        }
        Holder.Reference<Enchantment> holder = find(enchantmentId);
        return holder == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    boolean apply(@NotNull ItemStack stack, @NotNull String enchantmentId, int level) {
        if (stack.isEmpty()) {
            return false;
        }
        if (level < 0 || level > Enchantment.MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Enchantment level must be between 0 and " + Enchantment.MAX_LEVEL);
        }
        Holder.Reference<Enchantment> holder = find(enchantmentId);
        if (holder == null || !EnchantmentHelper.canStoreEnchantments(stack)) {
            return false;
        }
        if (level == 0) {
            EnchantmentHelper.updateEnchantments(stack,
                    mutable -> mutable.removeIf(existing -> existing.is(holder)));
        } else {
            EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder, level));
        }
        return true;
    }

    boolean remove(@NotNull ItemStack stack, @NotNull String enchantmentId) {
        return apply(stack, enchantmentId, 0);
    }

    /**
     * Resolves an enchantment id against the loaded enchantment registry (a datapack registry in
     * 1.21.1, so it is only available through a level/server registry access).
     *
     * @return the holder, or {@code null} when the id is malformed or unknown
     */
    private @Nullable Holder.Reference<Enchantment> find(@NotNull String enchantmentId) {
        ResourceLocation location = parseId(enchantmentId);
        if (location == null) {
            return null;
        }
        return registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(location)
                .orElse(null);
    }

    private RegistryAccess registryAccess() {
        if (owner instanceof Player player && player.level() != null) {
            return player.level().registryAccess();
        }
        MinecraftServer server = com.luatweaker.common.CommonServerHolder.get();
        if (server != null) {
            return server.registryAccess();
        }
        throw new IllegalStateException(
                "Item enchantment operations require a loaded level or a running server");
    }

    private static @Nullable ResourceLocation parseId(@NotNull String raw) {
        if (raw.isBlank()) {
            return null;
        }
        return raw.contains(":")
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, raw);
    }
}
