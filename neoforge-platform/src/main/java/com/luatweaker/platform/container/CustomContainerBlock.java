package com.luatweaker.platform.container;

import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.api.event.EventNames;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Generic Lua-configured container block. The grid geometry, drop behaviour,
 * per-slot customization and machine features (FE energy, fluid tank, GUI bars,
 * block-state variants) all come from the {@code Content.NewBlock(...):Container(..)}
 * builder. Right-click opens the container GUI (after the optional Lua handler);
 * breaking the block follows the configured drop mode:
 * <ul>
 *   <li>{@code packed}: only the container item drops, contents packed into its NBT
 *       (place it again to restore everything)</li>
 *   <li>{@code spill}: contents drop like a vanilla chest</li>
 *   <li>{@code none}: nothing drops</li>
 * </ul>
 *
 * <p>Optional block states (all JSON-free, auto-generated):
 * <ul>
 *   <li>{@code booleanState} — one {@code BooleanProperty} (e.g. "running"), toggled
 *       from Lua via {@code World:SetBlockState(..., { running = true })};</li>
 *   <li>{@code connectionState} — six connection properties (north/east/south/west/up/down)
 *       recomputed automatically when neighbors of the same block are placed or removed,
 *       so pipes visually connect without any Lua.</li>
 * </ul>
 */
public class CustomContainerBlock extends Block implements EntityBlock {

    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    public static final BooleanProperty[] CONNECTION_PROPS = {
            BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
            BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN
    };

    private final String crateId;
    private final String crateTitle;
    private final ContainerSpec spec;
    private final BiConsumer<Object, Object> rightClickHandler;
    private final java.util.function.BiFunction<Object, Object, Boolean> itemFilter;
    @Nullable
    private final BooleanProperty stateProperty;

    public CustomContainerBlock(Properties properties, String crateId, String crateTitle,
                               ContainerSpec spec,
                               @Nullable BiConsumer<Object, Object> rightClickHandler,
                               @Nullable java.util.function.BiFunction<Object, Object, Boolean> itemFilter) {
        super(properties);
        this.crateId = crateId;
        this.crateTitle = crateTitle;
        this.spec = spec;
        this.rightClickHandler = rightClickHandler;
        this.itemFilter = itemFilter;
        BooleanStateSpec stateSpec = spec.booleanState();
        this.stateProperty = stateSpec != null && stateSpec.property() != null
                ? BooleanProperty.create(stateSpec.property()) : null;
        registerDefaultState(defaultBlockState());
    }

    public String getContainerId() {
        return crateId;
    }

    public String getContainerTitle() {
        return crateTitle;
    }

    @NotNull
    public ContainerSpec getSpec() {
        return spec;
    }

    public int getSlotCount() {
        return spec.rows() * spec.cols();
    }

    public int getRows() {
        return spec.rows();
    }

    public int getCols() {
        return spec.cols();
    }

    public String getDropMode() {
        return spec.dropMode();
    }

    /** Max distance (blocks) a player may stand from this container to use it (Lua-configurable). */
    public double getUseDistance() {
        return spec.useDistance();
    }

    /** Lua-defined container rule: true = the item may enter a slot. */
    public boolean acceptsItem(net.minecraft.world.item.ItemStack stack) {
        if (itemFilter == null || stack == null || stack.isEmpty()) return true;
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try {
            return Boolean.TRUE.equals(itemFilter.apply(itemId, stack.getCount()));
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "Container itemFilter failed for " + crateId + ": " + e.getMessage());
            return false;
        }
    }

    @Nullable
    public BooleanProperty getStateProperty() {
        return stateProperty;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        if (stateProperty != null) {
            builder.add(stateProperty);
        }
        if (spec.connections()) {
            builder.add(CONNECTION_PROPS);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CustomContainerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || spec.tickHandler() == null) {
            return null;
        }
        net.minecraft.world.level.block.entity.BlockEntityType<?> ownType = CustomContainerRegistry.TYPE_BY_BLOCK.get(this);
        if (ownType == null || ownType != type) {
            return null;
        }
        return (level1, pos, state1, be) -> CustomContainerBlockEntity.machineTick(level1, pos, state1,
                (CustomContainerBlockEntity) be);
    }

    // ===== Pipe connection maintenance =====

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            updateConnections(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean isMoving) {
        if (!level.isClientSide() && spec.connections()) {
            updateConnections(level, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!newState.is(state.getBlock()) && spec.connections() && !level.isClientSide()) {
            // Neighbors still think they connect to this pipe - recompute them.
            for (Direction dir : ALL_DIRECTIONS) {
                BlockPos neighbor = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.getBlock() == this && neighborState.getValue(CONNECTION_PROPS[dir.get3DDataValue()])) {
                    updateConnections(level, neighbor, neighborState);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void updateConnections(Level level, BlockPos pos, BlockState state) {
        BlockState updated = state;
        for (int i = 0; i < ALL_DIRECTIONS.length; i++) {
            Direction dir = ALL_DIRECTIONS[i];
            boolean connected = level.getBlockState(pos.relative(dir)).getBlock() == this;
            updated = updated.setValue(CONNECTION_PROPS[i], connected);
        }
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    // ===== Interaction =====

    @Override
    @NotNull
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity container) {
            if (rightClickHandler != null) {
                try {
                    rightClickHandler.accept(new NeoForgePlayerWrapper(player), state);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed container right-click handler for " + crateId + ": " + e.getMessage());
                }
            }
            if (!container.stillValid(player)) {
                player.displayClientMessage(Component.translatable("container.luatweaker.locked"), true);
                return InteractionResult.FAIL;
            }
            java.util.OptionalInt menuId = player.openMenu(container);
            if (menuId.isEmpty()) {
                player.displayClientMessage(Component.translatable("container.luatweaker.locked"), true);
                return InteractionResult.FAIL;
            }
            final int fx = pos.getX();
            final int fy = pos.getY();
            final int fz = pos.getZ();
            ContainerEvents.post(EventNames.CONTAINER_OPENED, engine -> {
                com.luatweaker.api.vm.ILuaTable payload = ContainerEvents.basePayload(engine, this, fx, fy, fz);
                payload.rawset("Player", engine.wrapString(player.getName().getString()));
                return payload;
            });
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity container) {
            switch (spec.dropMode()) {
                case "packed" -> {
                    ItemStack packed = new ItemStack(this);
                    container.saveToItem(packed, level.registryAccess());
                    Block.popResource(level, pos, packed);
                }
                case "spill" -> {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack stack = container.getItem(i);
                        if (!stack.isEmpty()) {
                            Block.popResource(level, pos, stack);
                        }
                    }
                }
                default -> {
                    // "none": container is destroyed, contents vanish with it.
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
