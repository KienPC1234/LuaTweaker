package com.luatweaker.platform.container;

import com.luatweaker.api.event.EventNames;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Generic Lua-configured container block entity. The slot count is fixed by the
 * owning {@link CustomContainerBlock} (rows x cols from the Lua builder), so the
 * same block entity class serves any container size. Contents persist in NBT like a
 * vanilla chest ({@code Items} via {@link ContainerHelper}); any extra Lua NBT
 * fields are preserved through {@link #getExtraNbt()} / {@link #setExtraNbt()}.
 *
 * <p>Also implements {@link IItemHandler}, {@link IEnergyStorage} and
 * {@link IFluidHandler} (registered as the block capabilities), so external mods —
 * item pipes, energy cables, fluid ducts, scanner tools — read and move resources
 * through the standard NeoForge APIs. The Lua {@code itemFilter} rule applies to
 * pipe inserts as well. Energy/fluid/progress values are plain NBT keys
 * ({@code Energy}, {@code FluidId}, {@code FluidAmount}, {@code Progress}), so Lua
 * reads and writes them with the existing {@code World:GetBlockEntityData} /
 * {@code World:SetBlockEntityData} calls.</p>
 */
public class CustomContainerBlockEntity extends BlockEntity implements Container, MenuProvider, IItemHandler, IEnergyStorage, IFluidHandler {

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_EXTRA = "LuaData";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_FLUID_ID = "FluidId";
    private static final String TAG_FLUID_AMOUNT = "FluidAmount";
    private static final String TAG_PROGRESS = "Progress";

    /** Fallback size when the owning block could not be resolved (rare). */
    private static final int DEFAULT_SLOT_COUNT = 24;
    /** Fallback menu geometry when the owning block could not be resolved. */
    private static final int DEFAULT_ROWS = 4;
    private static final int DEFAULT_COLS = 6;
    /** Fallback use reach (blocks) when the owning block could not be resolved. */
    private static final double DEFAULT_USE_DISTANCE = 8.0;

    private final NonNullList<ItemStack> items;
    private CompoundTag extraNbt = new CompoundTag();

    // ===== Machine state (Lua-driven via World:SetBlockEntityData / GetBlockEntityData) =====
    private int energyStored;
    private String fluidId = "";
    private int fluidAmount;
    private float progress;

    public CustomContainerBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        super(resolveType(state), pos, state);
        CustomContainerBlock container = state.getBlock() instanceof CustomContainerBlock c ? c : null;
        int size = container != null ? container.getSlotCount() : DEFAULT_SLOT_COUNT;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<?> resolveType(BlockState state) {
        if (state.getBlock() instanceof CustomContainerBlock container) {
            net.minecraft.world.level.block.entity.BlockEntityType<CustomContainerBlockEntity> type = CustomContainerRegistry.TYPE_BY_BLOCK.get(container);
            if (type != null) return type;
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "No block entity type registered for container " + container.getContainerId() + " - using fallback");
        }
        return net.minecraft.world.level.block.entity.BlockEntityType.BARREL;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (energyStored > 0) {
            tag.putInt(TAG_ENERGY, energyStored);
        }
        if (!fluidId.isEmpty() && fluidAmount > 0) {
            tag.putString(TAG_FLUID_ID, fluidId);
            tag.putInt(TAG_FLUID_AMOUNT, fluidAmount);
        }
        if (progress > 0f) {
            tag.putFloat(TAG_PROGRESS, progress);
        }
        if (!extraNbt.isEmpty()) {
            tag.put(TAG_EXTRA, extraNbt);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        extraNbt = tag.getCompound(TAG_EXTRA);
        energyStored = tag.getInt(TAG_ENERGY);
        fluidId = tag.getString(TAG_FLUID_ID);
        fluidAmount = tag.getInt(TAG_FLUID_AMOUNT);
        progress = tag.getFloat(TAG_PROGRESS);
        clampMachineState();
    }

    private void clampMachineState() {
        ContainerSpec spec = resolveSpec();
        if (energyStored > spec.energyCapacity()) {
            energyStored = spec.energyCapacity();
        }
        if (energyStored < 0) {
            energyStored = 0;
        }
        if (fluidAmount > spec.fluidCapacity()) {
            fluidAmount = spec.fluidCapacity();
        }
        if (fluidAmount < 0 || fluidId.isEmpty()) {
            fluidAmount = 0;
            fluidId = "";
        }
        if (progress < 0f) {
            progress = 0f;
        } else if (progress > 1f) {
            progress = 1f;
        }
    }

    private ContainerSpec resolveSpec() {
        if (level != null && level.getBlockState(worldPosition).getBlock() instanceof CustomContainerBlock container) {
            return container.getSpec();
        }
        return new ContainerSpec(DEFAULT_ROWS, DEFAULT_COLS, "packed", null, DEFAULT_USE_DISTANCE,
                null, null, null, 0, 0, 0, 0, java.util.List.of(), null, false, null);
    }

    // ===== Machine state accessors (Lua + GUI DataSlots + capabilities) =====

    public int getEnergyCapacity() {
        return resolveSpec().energyCapacity();
    }

    public int getEnergyMaxReceive() {
        return resolveSpec().energyMaxReceive();
    }

    public int getEnergyMaxExtract() {
        return resolveSpec().energyMaxExtract();
    }

    public void setEnergyStored(int amount) {
        energyStored = Math.max(0, Math.min(amount, getEnergyCapacity()));
        setChanged();
    }

    public int getFluidAmount() {
        return fluidAmount;
    }

    public int getFluidCapacity() {
        return resolveSpec().fluidCapacity();
    }

    public String getFluidId() {
        return fluidId;
    }

    public void setFluid(String id, int amount) {
        int capacity = getFluidCapacity();
        if (capacity <= 0) {
            fluidId = "";
            fluidAmount = 0;
            return;
        }
        if (id == null || id.isBlank()) {
            fluidId = "";
            fluidAmount = 0;
        } else {
            fluidId = id;
            fluidAmount = Math.max(0, Math.min(amount, capacity));
        }
        setChanged();
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float value) {
        progress = value < 0f ? 0f : Math.min(value, 1f);
        setChanged();
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Extra NBT written by Lua via World:SetBlockEntityData (preserved across saves). */
    @NotNull
    public CompoundTag getExtraNbt() {
        return extraNbt;
    }

    public void setExtraNbt(@NotNull CompoundTag extraNbt) {
        this.extraNbt = extraNbt;
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (isSlotLocked(slot)) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (isSlotLocked(slot)) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            // Locked slots are read-only at the container level too - the GUI
            // already refuses them via ContainerFilteredSlot, this closes the
            // hopper/pipe/automation paths that bypass the menu entirely.
            if (!stack.isEmpty() && isSlotLocked(slot)) {
                fireRejected(stack, slot);
                return;
            }
            // Lua-defined container rule: rejected items never enter the container.
            if (!stack.isEmpty() && !acceptsStack(stack)) {
                fireRejected(stack, slot);
                return;
            }
            items.set(slot, stack);
            if (!stack.isEmpty() && stack.getCount() > stack.getMaxStackSize()) {
                stack.setCount(stack.getMaxStackSize());
            }
            setChanged();
        }
    }

    /** True when the Lua builder locked this slot (read-only storage). */
    public boolean isSlotLocked(int slot) {
        return resolveSpec().lockedSlots().contains(slot);
    }

    /** Lua-defined container rule, shared by GUI clicks and external IItemHandler inserts. */
    public boolean acceptsStack(ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return true;
        if (level.getBlockState(worldPosition).getBlock() instanceof CustomContainerBlock container) {
            return container.acceptsItem(stack);
        }
        return true;
    }

    private void fireRejected(ItemStack stack, int slot) {
        if (level == null) return;
        if (level.getBlockState(worldPosition).getBlock() instanceof CustomContainerBlock container) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            final int fx = worldPosition.getX();
            final int fy = worldPosition.getY();
            final int fz = worldPosition.getZ();
            ContainerEvents.post(EventNames.CONTAINER_ITEM_REJECTED, engine -> {
                com.luatweaker.api.vm.ILuaTable payload = ContainerEvents.basePayload(engine, container, fx, fy, fz);
                payload.rawset("ItemId", engine.wrapString(itemId));
                payload.rawset("Count", engine.wrapNumber(stack.getCount()));
                payload.rawset("Slot", engine.wrapNumber(slot));
                return payload;
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Container " + container.getContainerId() + " rejected item " + itemId + " (slot " + slot + ")");
        }
    }

    // ===== IItemHandler (external mods: pipes, automation, scanner tools) =====

    @Override
    public int getSlots() {
        return items.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= items.size()) return stack;
        if (isSlotLocked(slot)) {
            if (!simulate) fireRejected(stack, slot);
            return stack;
        }
        ItemStack existing = getItem(slot);
        if (!acceptsStack(stack)) {
            if (!simulate) fireRejected(stack, slot);
            return stack;
        }
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
        int max = Math.min(stack.getMaxStackSize(), getSlotLimit(slot));
        int space = max - existing.getCount();
        if (space <= 0) return stack;
        int taken = Math.min(space, stack.getCount());
        ItemStack remainder = stack.copy();
        remainder.shrink(taken);
        if (!simulate) {
            if (existing.isEmpty()) {
                setItem(slot, stack.copyWithCount(taken));
            } else {
                existing.grow(taken);
                setChanged();
            }
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        if (isSlotLocked(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = getItem(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        int taken = Math.min(amount, existing.getCount());
        ItemStack result = existing.copyWithCount(taken);
        if (!simulate) {
            existing.shrink(taken);
            if (existing.isEmpty()) {
                setItem(slot, ItemStack.EMPTY);
            } else {
                setChanged();
            }
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !isSlotLocked(slot) && acceptsStack(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        double distance = DEFAULT_USE_DISTANCE;
        if (level.getBlockState(worldPosition).getBlock() instanceof CustomContainerBlock container) {
            distance = container.getUseDistance();
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= distance * distance;
    }

    // ===== IEnergyStorage (external mods: cables, generators, batteries) =====

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int rate = getEnergyMaxReceive();
        if (rate <= 0 || maxReceive <= 0) {
            return 0;
        }
        int capacity = getEnergyCapacity();
        int space = capacity - energyStored;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(Math.min(maxReceive, rate), space);
        if (!simulate) {
            energyStored += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int rate = getEnergyMaxExtract();
        if (rate <= 0 || maxExtract <= 0 || energyStored <= 0) {
            return 0;
        }
        int taken = Math.min(Math.min(maxExtract, rate), energyStored);
        if (!simulate) {
            energyStored -= taken;
            setChanged();
        }
        return taken;
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    @Override
    public int getMaxEnergyStored() {
        return getEnergyCapacity();
    }

    @Override
    public boolean canExtract() {
        return getEnergyMaxExtract() > 0;
    }

    @Override
    public boolean canReceive() {
        return getEnergyMaxReceive() > 0;
    }

    // ===== IFluidHandler (external mods: ducts, pumps, fluid storage) =====

    @Override
    public int getTanks() {
        return getFluidCapacity() > 0 ? 1 : 0;
    }

    @Override
    @NotNull
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0 || fluidId.isEmpty() || fluidAmount <= 0) {
            return FluidStack.EMPTY;
        }
        net.minecraft.world.level.material.Fluid fluid =
                net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.tryParse(fluidId));
        if (fluid == null || fluid.isSame(net.minecraft.world.level.material.Fluids.EMPTY)) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid, fluidAmount);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? getFluidCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return tank == 0 && !stack.isEmpty() && getFluidCapacity() > 0;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        if (resource.isEmpty() || getFluidCapacity() <= 0) {
            return 0;
        }
        String incoming = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
        if (!fluidId.isEmpty() && !fluidId.equals(incoming)) {
            return 0;
        }
        int space = getFluidCapacity() - fluidAmount;
        int accepted = Math.min(resource.getAmount(), space);
        if (accepted <= 0) {
            return 0;
        }
        if (action.execute()) {
            fluidId = incoming;
            fluidAmount += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    @NotNull
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
        if (resource.isEmpty() || fluidId.isEmpty() || fluidAmount <= 0) {
            return FluidStack.EMPTY;
        }
        String requested = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
        if (!requested.equals(fluidId)) {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    @NotNull
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
        if (maxDrain <= 0 || fluidId.isEmpty() || fluidAmount <= 0) {
            return FluidStack.EMPTY;
        }
        int taken = Math.min(maxDrain, fluidAmount);
        net.minecraft.world.level.material.Fluid fluid =
                net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.tryParse(fluidId));
        if (fluid == null || fluid.isSame(net.minecraft.world.level.material.Fluids.EMPTY)) {
            return FluidStack.EMPTY;
        }
        if (action.execute()) {
            fluidAmount -= taken;
            if (fluidAmount <= 0) {
                fluidId = "";
            }
            setChanged();
        }
        return new FluidStack(fluid, taken);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ===== Lua machine behavior (server tick) =====

    /**
     * Calls the Lua {@code :OnTick} handler once per tick with a data table
     * (X, Y, Z, Energy, EnergyCapacity, FluidId, FluidAmount, FluidCapacity,
     * Progress). The handler reads and writes the block entity through the World
     * APIs. The engine never implements machine/pipe semantics itself - that is
     * Lua/addon territory.
     */
    public static void machineTick(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                   net.minecraft.world.level.block.state.BlockState state, CustomContainerBlockEntity be) {
        if (level.isClientSide() || be.isRemoved()) {
            return;
        }
        java.util.function.BiConsumer<Object, Object> handler = be.resolveSpec().tickHandler();
        if (handler == null) {
            return;
        }
        java.util.Map<String, Object> data = java.util.Map.of(
                "X", pos.getX(), "Y", pos.getY(), "Z", pos.getZ(),
                "Energy", be.energyStored, "EnergyCapacity", be.getEnergyCapacity(),
                "FluidId", be.fluidId, "FluidAmount", be.fluidAmount, "FluidCapacity", be.getFluidCapacity(),
                "Progress", be.progress);
        try {
            handler.accept(data, null);
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed Lua onTick handler at " + pos + ": " + e.getMessage());
        }
    }

    @Override
    @NotNull
    public Component getDisplayName() {
        if (level != null && level.getBlockState(worldPosition).getBlock() instanceof CustomContainerBlock container) {
            return Component.literal(container.getContainerTitle());
        }
        return Component.translatable("container.luatweaker.container");
    }

    @Override
    @NotNull
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        net.minecraft.world.inventory.MenuType<CustomContainerMenu> type = null;
        int rows = DEFAULT_ROWS;
        int cols = DEFAULT_COLS;
        java.util.Map<Integer, int[]> slotPositions = null;
        java.util.Set<Integer> lockedSlots = null;
        if (level != null && level.getBlockState(worldPosition).getBlock() instanceof CustomContainerBlock container) {
            rows = container.getRows();
            cols = container.getCols();
            slotPositions = container.getSpec().slotPositions();
            lockedSlots = container.getSpec().lockedSlots();
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(container.getContainerId());
            if (id != null) {
                type = CustomContainerRegistry.CONTAINER_MENUS.get(id);
            }
        }
        return new CustomContainerMenu(type, containerId, playerInventory, this, rows, cols, slotPositions, lockedSlots);
    }
}
