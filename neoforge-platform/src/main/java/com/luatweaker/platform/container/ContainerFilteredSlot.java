package com.luatweaker.platform.container;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Container slot that consults the Lua {@code itemFilter} rule before an item can
 * be placed. Rejecting in {@link #mayPlace} (instead of silently ignoring the
 * write in the container) keeps the dragged item on the player's cursor — it
 * never vanishes, it simply does not enter the container.
 *
 * <p>Slots configured as locked by the Lua builder also refuse pickups
 * ({@link #mayPickup}) and shift-clicks, making them read-only storage.</p>
 */
public class ContainerFilteredSlot extends Slot {

    private final boolean locked;

    public ContainerFilteredSlot(@NotNull Container container, int slot, int x, int y) {
        this(container, slot, x, y, false);
    }

    public ContainerFilteredSlot(@NotNull Container container, int slot, int x, int y, boolean locked) {
        super(container, slot, x, y);
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        if (locked) {
            return false;
        }
        if (container instanceof CustomContainerBlockEntity container) {
            return container.acceptsStack(stack);
        }
        return super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return !locked && super.mayPickup(player);
    }
}
