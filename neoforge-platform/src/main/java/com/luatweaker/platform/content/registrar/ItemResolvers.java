package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IItemBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.Locale;
import java.util.Map;

/** Pure resolvers translating Lua item-builder fields into vanilla item types. */
public final class ItemResolvers {
    /** Vanilla sword attack speed when the Lua builder does not set one. */
    static final float DEFAULT_ATTACK_SPEED = -2.4f;
    static final int DEFAULT_FOOD_USE_TICKS = 32;
    private static final float VANILLA_SWORD_SPEED = 4.0f;
    private static final int VANILLA_TOOL_ENCHANTABILITY = 10;
    private static final int NETHERITE_MIN_LEVEL = 4;
    private static final int NETHERITE_MAX_LEVEL = 10;

    private static final Map<String, UseAnim> USE_ANIMATIONS = Map.ofEntries(
            Map.entry("EAT", UseAnim.EAT),
            Map.entry("DRINK", UseAnim.DRINK),
            Map.entry("BLOCK", UseAnim.BLOCK),
            Map.entry("BOW", UseAnim.BOW),
            Map.entry("SPEAR", UseAnim.SPEAR),
            Map.entry("CROSSBOW", UseAnim.CROSSBOW),
            Map.entry("SPYGLASS", UseAnim.SPYGLASS),
            Map.entry("TOOT_HORN", UseAnim.TOOT_HORN),
            Map.entry("BRUSH", UseAnim.BRUSH));

    private ItemResolvers() {
    }

    static UseAnim resolveUseAnim(String name) {
        if (name == null || name.isBlank()) {
            return UseAnim.NONE;
        }
        return USE_ANIMATIONS.getOrDefault(name.toUpperCase(Locale.ROOT), UseAnim.NONE);
    }

    static int resolveUseDuration(IItemBuilder builder) {
        if (builder.getUseDuration() > 0) {
            return builder.getUseDuration();
        }
        return builder.getFoodHunger() > 0 ? DEFAULT_FOOD_USE_TICKS : 0;
    }

    static Tier resolveTier(IItemBuilder builder) {
        Tier base = baseTier(builder.getMiningLevel());
        float customSpeed = builder.getMiningSpeed();
        int customDurability = builder.getDurability();
        int customEnchant = builder.getEnchantability();
        if (customSpeed == VANILLA_SWORD_SPEED && customDurability <= 0
                && customEnchant == VANILLA_TOOL_ENCHANTABILITY) {
            return base;
        }
        return customTier(base, customSpeed, customDurability, customEnchant);
    }

    private static Tier baseTier(int miningLevel) {
        if (miningLevel >= NETHERITE_MIN_LEVEL && miningLevel <= NETHERITE_MAX_LEVEL) {
            return Tiers.NETHERITE;
        }
        return switch (miningLevel) {
            case 0 -> Tiers.WOOD;
            case 1 -> Tiers.STONE;
            case 3 -> Tiers.DIAMOND;
            default -> Tiers.IRON;
        };
    }

    private static Tier customTier(Tier base, float speed, int durability, int enchantability) {
        return new Tier() {
            @Override
            public int getUses() {
                return durability > 0 ? durability : base.getUses();
            }

            @Override
            public float getSpeed() {
                return speed > 0 ? speed : base.getSpeed();
            }

            @Override
            public float getAttackDamageBonus() {
                return base.getAttackDamageBonus();
            }

            @Override
            public TagKey<Block> getIncorrectBlocksForDrops() {
                return base.getIncorrectBlocksForDrops();
            }

            @Override
            public int getEnchantmentValue() {
                return enchantability > 0 ? enchantability : base.getEnchantmentValue();
            }

            @Override
            public Ingredient getRepairIngredient() {
                return base.getRepairIngredient();
            }
        };
    }
}

