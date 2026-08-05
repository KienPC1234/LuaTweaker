package com.luatweaker.platform.container;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Container slot that consults the Lua {@code itemFilter} rule before an item can
 * be placed. Rejecting in {@link #mayPlace} (instead of silently ignoring the
 * write in the container) keeps the dragged item on the player's cursor — it
 * never vanishes, it simply does not enter the container.
 */
public class ContainerFilteredSlot extends Slot {

    public ContainerFilteredSlot(@NotNull Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        if (container instanceof CustomContainerBlockEntity container) {
            return container.acceptsStack(stack);
        }
        return super.mayPlace(stack);
    }
}
