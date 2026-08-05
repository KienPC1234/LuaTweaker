package com.luatweaker.platform.container;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic container menu for Lua-configured container blocks. The container region is a
 * grid of {@code rows} rows x {@code cols} columns (indices 0..rows*cols-1);
 * the player inventory follows (indices rows*cols..+35).
 */
public class CustomContainerMenu extends AbstractContainerMenu {

    private static final int CRATE_START_X = 34;
    private static final int CRATE_START_Y = 17;
    private static final int PLAYER_START_X = 8;
    private static final int PLAYER_START_Y = 107;
    private static final int HOTBAR_Y = 165;

    private final Container container;
    private final int containerRows;
    private final int containerCols;

    public CustomContainerMenu(@Nullable MenuType<?> menuType, int containerId, @NotNull Inventory playerInventory,
                              @NotNull Container container, int containerRows, int containerCols) {
        super(menuType, containerId);
        this.container = container;
        this.containerRows = containerRows;
        this.containerCols = containerCols;

        for (int row = 0; row < containerRows; row++) {
            for (int col = 0; col < containerCols; col++) {
                addSlot(new ContainerFilteredSlot(container, row * containerCols + col,
                        CRATE_START_X + col * 18, CRATE_START_Y + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_START_X + col * 18, PLAYER_START_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_START_X + col * 18, HOTBAR_Y));
        }
    }

    public int getContainerRows() {
        return containerRows;
    }

    public int getContainerCols() {
        return containerCols;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return container.stillValid(player);
    }

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < container.getContainerSize()) {
                if (!moveItemStackTo(stack, container.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, container.getContainerSize(), false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return moved;
    }
}
