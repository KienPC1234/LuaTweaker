package com.luatweaker.platform.crate;

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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Generic Lua-configured container block entity. The slot count is fixed by the
 * owning {@link ContainerCrateBlock} (rows x cols from the Lua builder), so the
 * same block entity class serves any crate size. Contents persist in NBT like a
 * vanilla chest ({@code Items} via {@link ContainerHelper}); any extra Lua NBT
 * fields are preserved through {@link #getExtraNbt()} / {@link #setExtraNbt()}.
 *
 * <p>Also implements {@link IItemHandler} (registered as the block capability),
 * so external mods — item pipes, hopper-like automation, scanner tools — read
 * and move items through the standard NeoForge API. The Lua {@code itemFilter}
 * rule applies to pipe inserts as well.</p>
 */
public class ContainerCrateBlockEntity extends BlockEntity implements Container, MenuProvider, IItemHandler {

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_EXTRA = "LuaData";

    private final NonNullList<ItemStack> items;
    private CompoundTag extraNbt = new CompoundTag();

    public ContainerCrateBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        super(resolveType(state), pos, state);
        ContainerCrateBlock crate = state.getBlock() instanceof ContainerCrateBlock c ? c : null;
        int size = crate != null ? crate.getSlotCount() : 24;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<?> resolveType(BlockState state) {
        if (state.getBlock() instanceof ContainerCrateBlock crate) {
            net.minecraft.world.level.block.entity.BlockEntityType<ContainerCrateBlockEntity> type = ContainerCrateRegistry.TYPE_BY_BLOCK.get(crate);
            if (type != null) return type;
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "No block entity type registered for crate " + crate.getCrateId() + " - using fallback");
        }
        return net.minecraft.world.level.block.entity.BlockEntityType.BARREL;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
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
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            // Lua-defined container rule: rejected items never enter the crate.
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

    /** Lua-defined container rule, shared by GUI clicks and external IItemHandler inserts. */
    public boolean acceptsStack(ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return true;
        if (level.getBlockState(worldPosition).getBlock() instanceof ContainerCrateBlock crate) {
            return crate.acceptsItem(stack);
        }
        return true;
    }

    private void fireRejected(ItemStack stack, int slot) {
        if (level == null) return;
        if (level.getBlockState(worldPosition).getBlock() instanceof ContainerCrateBlock crate) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            final int fx = worldPosition.getX();
            final int fy = worldPosition.getY();
            final int fz = worldPosition.getZ();
            CrateEvents.post("CrateItemRejected", engine -> {
                com.luatweaker.api.vm.ILuaTable payload = CrateEvents.basePayload(engine, crate, fx, fy, fz);
                payload.rawset("ItemId", engine.wrapString(itemId));
                payload.rawset("Count", engine.wrapNumber(stack.getCount()));
                payload.rawset("Slot", engine.wrapNumber(slot));
                return payload;
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Crate " + crate.getCrateId() + " rejected item " + itemId + " (slot " + slot + ")");
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
        return acceptsStack(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    @NotNull
    public Component getDisplayName() {
        if (level != null && level.getBlockState(worldPosition).getBlock() instanceof ContainerCrateBlock crate) {
            return Component.literal(crate.getCrateTitle());
        }
        return Component.translatable("container.luatweaker.crate");
    }

    @Override
    @NotNull
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        net.minecraft.world.inventory.MenuType<ContainerCrateMenu> type = null;
        int rows = 4;
        int cols = 6;
        if (level != null && level.getBlockState(worldPosition).getBlock() instanceof ContainerCrateBlock crate) {
            rows = crate.getRows();
            cols = crate.getCols();
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(crate.getCrateId());
            if (id != null) {
                type = ContainerCrateRegistry.CRATE_MENUS.get(id);
            }
        }
        return new ContainerCrateMenu(type, containerId, playerInventory, this, rows, cols);
    }
}
