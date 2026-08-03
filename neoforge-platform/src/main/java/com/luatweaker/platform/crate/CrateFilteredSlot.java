package com.luatweaker.platform.crate;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Crate slot that consults the Lua {@code itemFilter} rule before an item can
 * be placed. Rejecting in {@link #mayPlace} (instead of silently ignoring the
 * write in the container) keeps the dragged item on the player's cursor — it
 * never vanishes, it simply does not enter the crate.
 */
public class CrateFilteredSlot extends Slot {

    public CrateFilteredSlot(@NotNull Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        if (container instanceof ContainerCrateBlockEntity crate) {
            return crate.acceptsStack(stack);
        }
        return super.mayPlace(stack);
    }
}
