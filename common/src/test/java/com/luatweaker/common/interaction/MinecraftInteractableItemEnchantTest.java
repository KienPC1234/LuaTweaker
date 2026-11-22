package com.luatweaker.common.interaction;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the input guards of the new item enchantment primitives without booting a server. */
class MinecraftInteractableItemEnchantTest {
    private static final String SHARPNESS = "minecraft:sharpness";

    @Test
    void emptyStackHasNoEnchantments() {
        MinecraftInteractableItem item = new MinecraftInteractableItem(ItemStack.EMPTY);

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertTrue(item.getEnchantments().isEmpty(), "an empty stack must report no enchantments"),
                () -> assertEquals(0, item.getEnchantmentLevel(SHARPNESS), "an empty stack must report level 0")
        );
    }

    @Test
    void outOfRangeLevelIsRejectedBeforeRegistryLookup() {
        MinecraftInteractableItem item =
                new MinecraftInteractableItem(new ItemStack(Items.DIAMOND_SWORD));

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> item.applyEnchantment(SHARPNESS, 256),
                        "a level above the vanilla cap must fail loudly"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> item.applyEnchantment(SHARPNESS, -1),
                        "a negative level must fail loudly")
        );
    }

    @Test
    void emptyStackCannotBeEnchanted() {
        MinecraftInteractableItem item = new MinecraftInteractableItem(ItemStack.EMPTY);

        assertFalse(item.applyEnchantment(SHARPNESS, 1),
                "an empty stack must not report a successful enchantment");
    }
}
