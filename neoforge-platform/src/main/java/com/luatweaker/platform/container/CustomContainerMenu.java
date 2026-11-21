package com.luatweaker.platform.container;

import com.luatweaker.api.content.MachineBarSpec;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

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
public final class CustomContainerMenu extends AbstractContainerMenu {

    private final Container container;
    private final int containerRows;
    private final int containerCols;
    private final int containerSlotCount;
    /** Progress is synced as fixed-point (0..1000) through the vanilla data-slot pipeline.
     * Intentionally fixed protocol constant: menu and screen must agree, not per-mod config. */
    private static final int PROGRESS_SCALE = 1000;
    /** Vanilla player inventory dimensions (fixed by Minecraft, not tunable). */
    private static final int PLAYER_INV_ROWS = 3;
    private static final int PLAYER_INV_COLS = 9;

    private int energyStored;
    private int energyCapacity;
    private int fluidAmount;
    private int fluidCapacity;
    private int progressFixed;
    private int blockPosX;
    private int blockPosY;
    private int blockPosZ;
    /** Data-slot writers run on the network thread; the render thread reads it. */
    private final Map<String, Integer> customValues = new ConcurrentHashMap<>();

    public CustomContainerMenu(@Nullable MenuType<?> menuType, int containerId, @NotNull Inventory playerInventory,
                               @NotNull Container container, int containerRows, int containerCols) {
        this(menuType, containerId, playerInventory, container, containerRows, containerCols,
                null, null, false);
    }

    public CustomContainerMenu(@Nullable MenuType<?> menuType, int containerId, @NotNull Inventory playerInventory,
                               @NotNull Container container, int containerRows, int containerCols,
                               @Nullable Map<Integer, int[]> slotPositions,
                               @Nullable Set<Integer> lockedSlots, boolean manualSlots) {
        super(menuType, containerId);
        this.container = container;
        this.containerRows = containerRows;
        this.containerCols = containerCols;
        List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                containerRows, containerCols, slotPositions, lockedSlots, manualSlots);
        this.containerSlotCount = planned.size();
        addContainerSlots(container, planned);
        addPlayerSlots(playerInventory);
        CustomContainerBlockEntity machine = container instanceof CustomContainerBlockEntity be ? be : null;
        addMachineDataSlots(machine, menuType);
    }

    private void addContainerSlots(Container container,
                                     List<ContainerSlotPlanner.PlacedSlot> planned) {
        for (ContainerSlotPlanner.PlacedSlot slot : planned) {
            addSlot(containerSlot(container, slot.index(), slot.x(), slot.y(), slot.locked()));
        }
    }

    private static Slot containerSlot(Container container, int index, int x, int y, boolean locked) {
        return new ContainerFilteredSlot(container, index, x, y, locked);
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < PLAYER_INV_ROWS; row++) {
            for (int col = 0; col < PLAYER_INV_COLS; col++) {
                addSlot(playerSlot(playerInventory, col + row * PLAYER_INV_COLS + PLAYER_INV_COLS,
                        col, ContainerLayout.playerY(row, containerRows)));
            }
        }
        for (int col = 0; col < PLAYER_INV_COLS; col++) {
            addSlot(playerSlot(playerInventory, col, col, ContainerLayout.hotbarY(containerRows)));
        }
    }

    private static Slot playerSlot(Inventory playerInventory, int index, int col, int y) {
        return new Slot(playerInventory, index, ContainerLayout.LEFT_MARGIN + col * ContainerLayout.CELL, y);
    }

    /** Machine values synced to the client while the menu is open (bars). */
    private void addMachineDataSlots(@Nullable CustomContainerBlockEntity machine, @Nullable MenuType<?> menuType) {
        addCoreMachineDataSlots(machine);
        List<MachineBarSpec> bars = menuType != null ? CustomContainerRegistry.CONTAINER_BARS.get(menuType) : null;
        if (bars == null) {
            return;
        }
        for (MachineBarSpec bar : bars) {
            if (bar.currentKey() != null) {
                String currentKey = bar.currentKey();
                addMachineDataSlot(() -> readMachineValue(machine, m -> m.getCustomNumericValue(currentKey)),
                        value -> customValues.put(currentKey, value));
            }
            if (bar.maxKey() != null) {
                String maxKey = bar.maxKey();
                addMachineDataSlot(() -> readMachineValue(machine, m -> m.getCustomNumericValue(maxKey)),
                        value -> customValues.put(maxKey, value));
            }
        }
    }

    public int getCustomValue(@Nullable String key) {
        return key != null ? customValues.getOrDefault(key, 0) : 0;
    }

    /** A synced data-slot value, or null when the key was never synced. */
    @Nullable Integer getCustomValueOrNull(@Nullable String key) {
        return key == null ? null : customValues.get(key);
    }

    /**
     * Reads one value from the machine, or {@code 0} when the menu was opened without a
     * machine (client-side screens read the value back from the synced data slots instead).
     */
    private static int readMachineValue(@Nullable CustomContainerBlockEntity machine,
                                        ToIntFunction<CustomContainerBlockEntity> reader) {
        return machine != null ? reader.applyAsInt(machine) : 0;
    }

    private void addCoreMachineDataSlots(@Nullable CustomContainerBlockEntity machine) {
        addMachineDataSlot(() -> readMachineValue(machine, CustomContainerBlockEntity::getEnergyStored),
                value -> energyStored = value);
        addMachineDataSlot(() -> readMachineValue(machine, CustomContainerBlockEntity::getEnergyCapacity),
                value -> energyCapacity = value);
        addMachineDataSlot(() -> readMachineValue(machine, CustomContainerBlockEntity::getFluidAmount),
                value -> fluidAmount = value);
        addMachineDataSlot(() -> readMachineValue(machine, CustomContainerBlockEntity::getFluidCapacity),
                value -> fluidCapacity = value);
        addMachineDataSlot(() -> readMachineValue(machine, m -> Math.round(m.getProgress() * PROGRESS_SCALE)),
                value -> progressFixed = value);
        addMachineDataSlot(() -> readMachineValue(machine, m -> m.getBlockPos().getX()), value -> blockPosX = value);
        addMachineDataSlot(() -> readMachineValue(machine, m -> m.getBlockPos().getY()), value -> blockPosY = value);
        addMachineDataSlot(() -> readMachineValue(machine, m -> m.getBlockPos().getZ()), value -> blockPosZ = value);
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

    public int getContainerRows() {
        return containerRows;
    }

    public int getContainerCols() {
        return containerCols;
    }

    public net.minecraft.core.BlockPos getBlockPos() {
        return new net.minecraft.core.BlockPos(blockPosX, blockPosY, blockPosZ);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return container.stillValid(player);
    }

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (slot instanceof ContainerFilteredSlot filtered && filtered.isLocked()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        boolean movedToInventory = index < containerSlotCount
                ? moveItemStackTo(stack, containerSlotCount, this.slots.size(), true)
                : moveItemStackTo(stack, 0, containerSlotCount, false);
        if (!movedToInventory) {
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
        return moved;
    }
}



