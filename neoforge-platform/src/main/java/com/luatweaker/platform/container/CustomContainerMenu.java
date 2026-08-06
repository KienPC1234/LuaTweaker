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

import java.util.Map;
import java.util.Set;

/**
 * Generic container menu for Lua-configured container blocks. The container region is a
 * {@code rows} x {@code cols} grid (indices 0..rows*cols-1) following the vanilla chest
 * geometry (centered horizontally, {@link ContainerLayout}); the player inventory follows
 * (indices rows*cols..+35) and shifts down as the grid grows so nothing overlaps.
 *
 * <p>Per-slot customization from the Lua builder is honored: {@code slotPosition} overrides
 * the grid position of a single slot and {@code lockSlot} makes a slot reject insertions
 * and pickups (shift-click included).</p>
 */
public class CustomContainerMenu extends AbstractContainerMenu {

    private final Container container;
    private final int containerRows;
    private final int containerCols;
    private final int startX;
    /** Progress is synced as fixed-point (0..1000) through the vanilla data-slot pipeline. */
    private static final int PROGRESS_SCALE = 1000;

    private int energyStored;
    private int energyCapacity;
    private int fluidAmount;
    private int fluidCapacity;
    private int progressFixed;

    public CustomContainerMenu(@Nullable MenuType<?> menuType, int containerId, @NotNull Inventory playerInventory,
                              @NotNull Container container, int containerRows, int containerCols) {
        this(menuType, containerId, playerInventory, container, containerRows, containerCols, null, null);
    }

    public CustomContainerMenu(@Nullable MenuType<?> menuType, int containerId, @NotNull Inventory playerInventory,
                              @NotNull Container container, int containerRows, int containerCols,
                              @Nullable Map<Integer, int[]> slotPositions,
                              @Nullable Set<Integer> lockedSlots) {
        super(menuType, containerId);
        this.container = container;
        this.containerRows = containerRows;
        this.containerCols = containerCols;
        this.startX = ContainerLayout.containerStartX(containerCols);

        for (int row = 0; row < containerRows; row++) {
            for (int col = 0; col < containerCols; col++) {
                int index = row * containerCols + col;
                int x = defaultX(col);
                int y = ContainerLayout.containerY(row);
                int[] custom = slotPositions != null ? slotPositions.get(index) : null;
                if (custom != null && custom.length >= 2) {
                    x = custom[0];
                    y = custom[1];
                }
                boolean locked = lockedSlots != null && lockedSlots.contains(index);
                addSlot(new ContainerFilteredSlot(container, index, x, y, locked));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        ContainerLayout.LEFT_MARGIN + col * 18, ContainerLayout.playerY(row, containerRows)));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, ContainerLayout.LEFT_MARGIN + col * 18, ContainerLayout.hotbarY(containerRows)));
        }

        // Machine values synced to the client while the menu is open (bars).
        CustomContainerBlockEntity machine = container instanceof CustomContainerBlockEntity be ? be : null;
        addMachineDataSlot(() -> machine != null ? machine.getEnergyStored() : 0, v -> energyStored = v);
        addMachineDataSlot(() -> machine != null ? machine.getEnergyCapacity() : 0, v -> energyCapacity = v);
        addMachineDataSlot(() -> machine != null ? machine.getFluidAmount() : 0, v -> fluidAmount = v);
        addMachineDataSlot(() -> machine != null ? machine.getFluidCapacity() : 0, v -> fluidCapacity = v);
        addMachineDataSlot(() -> machine != null ? Math.round(machine.getProgress() * PROGRESS_SCALE) : 0, v -> progressFixed = v);
    }

    private void addMachineDataSlot(java.util.function.IntSupplier reader, java.util.function.IntConsumer writer) {
        this.addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override
            public int get() {
                return reader.getAsInt();
            }

            @Override
            public void set(int value) {
                writer.accept(value);
            }
        });
    }

    public int getEnergyStored() {
        return energyStored;
    }

    public int getEnergyCapacity() {
        return energyCapacity;
    }

    public int getFluidAmount() {
        return fluidAmount;
    }

    public int getFluidCapacity() {
        return fluidCapacity;
    }

    /** Progress 0..1, synced as fixed-point while the menu is open. */
    public float getProgress() {
        return progressFixed / (float) PROGRESS_SCALE;
    }

    private int defaultX(int col) {
        return startX + col * 18;
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
            if (slot instanceof ContainerFilteredSlot filtered && filtered.isLocked()) {
                return ItemStack.EMPTY;
            }
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
