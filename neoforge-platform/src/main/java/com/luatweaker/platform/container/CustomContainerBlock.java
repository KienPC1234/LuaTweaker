package com.luatweaker.platform.container;

import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Lua-configured container or functional machine block.
 * Supports inventory grids, FE energy, fluid storage, per-tick handlers,
 * custom voxel bounds, placement facing, and BER rendering.
 */
public final class CustomContainerBlock extends CustomBlock implements EntityBlock {

    private static final ThreadLocal<ContainerSpec> INIT_SPEC = new ThreadLocal<>();

    private static Properties pushSpec(Properties props, ContainerSpec spec) {
        INIT_SPEC.set(spec);
        return props;
    }

    private final String crateTitle;
    /** Replaced on {@code /lt reload}; read every tick/frame, hence atomic. */
    private final AtomicReference<ContainerSpec> spec;
    private final AtomicReference<BiFunction<Object, Object, Boolean>> itemFilter;
    @Nullable
    private final BooleanProperty stateProperty;

    public CustomContainerBlock(Properties properties, String crateId, String crateTitle,
                                ContainerSpec spec,
                                @Nullable BiConsumer<Object, Object> rightClickHandler,
                                @Nullable BiFunction<Object, Object, Boolean> itemFilter) {
        this(pushSpec(properties, spec), crateId, crateTitle, spec, rightClickHandler, itemFilter, true);
    }

    private CustomContainerBlock(Properties properties, String crateId, String crateTitle,
                                 ContainerSpec spec,
                                 @Nullable BiConsumer<Object, Object> rightClickHandler,
                                 @Nullable BiFunction<Object, Object, Boolean> itemFilter,
                                 boolean unused) {
        super(properties, crateId, spec != null ? spec.facing() : null,
                spec != null ? spec.boxes() : java.util.List.of(), rightClickHandler);
        INIT_SPEC.remove();
        this.crateTitle = crateTitle;
        this.spec = new AtomicReference<>(spec);
        this.itemFilter = new AtomicReference<>(itemFilter);
        BooleanStateSpec stateSpec = spec != null ? spec.booleanState() : null;
        this.stateProperty = resolveStateProperty(stateSpec);
        registerDefaultState(initialDefaultState(defaultBlockState(), spec, this.stateProperty));
    }

    @Nullable
    private BooleanProperty resolveStateProperty(@Nullable BooleanStateSpec stateSpec) {
        if (stateSpec == null || stateSpec.property() == null) {
            return null;
        }
        return (BooleanProperty) getStateDefinition().getProperty(stateSpec.property());
    }

    private static BlockState initialDefaultState(BlockState state, @Nullable ContainerSpec spec,
                                                  @Nullable BooleanProperty stateProperty) {
        BlockState result = stateProperty != null ? state.setValue(stateProperty, false) : state;
        if (spec != null && spec.connections()) {
            for (BooleanProperty property : PipeConnections.properties()) {
                result = result.setValue(property, false);
            }
        }
        return result;
    }

    public String getContainerId() {
        return blockId;
    }

    public String getContainerTitle() {
        return crateTitle;
    }

    @NotNull
    public ContainerSpec getSpec() {
        return spec.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (spec.get().geoModel() != null) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }
        return super.getRenderShape(state);
    }

    /**
     * Adopts the behaviour of a freshly loaded builder after {@code /lt reload}.
     *
     * <p>Registry entries, block-state properties, shape and facing are frozen by
     * NeoForge, so those keep their original values (and a warning is logged when the
     * script tried to change one). Everything read at runtime is replaced: tick handler,
     * item filter, right-click handler, energy/fluid capacity, GUI layout, BER handler.</p>
     *
     * @param refreshed     spec rebuilt from the current builder
     * @param newItemFilter current {@code :ItemFilter} callback (may be null)
     * @param newRightClick current {@code :OnRightClick} callback (may be null)
     * @return true when the block behaviour was replaced
     */
    public boolean rebind(ContainerSpec refreshed, @Nullable BiFunction<Object, Object, Boolean> newItemFilter,
                          @Nullable BiConsumer<Object, Object> newRightClick) {
        if (refreshed == null) {
            return false;
        }
        ContainerSpec previous = this.spec.get();
        if (previous.hasFrozenDifference(refreshed)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Block '" + blockId + "' changed geometry/blockstate/shape on reload;"
                            + " that requires a game restart and was not applied"
                            + " (live behaviour was refreshed)");
        }
        if (previous.renderHandler() == null && refreshed.renderHandler() != null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Block '" + blockId + "' added :OnRender on reload; the block entity renderer"
                            + " is registered at load, so restart the game to see it");
        }
        this.spec.set(ContainerSpec.adoptRuntime(previous, refreshed));
        this.itemFilter.set(newItemFilter);
        setRightClickHandler(newRightClick);
        return true;
    }

    public int getSlotCount() {
        return spec.get().rows() * spec.get().cols();
    }

    public int getRows() {
        return spec.get().rows();
    }

    public int getCols() {
        return spec.get().cols();
    }

    public String getDropMode() {
        return spec.get().dropMode();
    }

    public double getUseDistance() {
        return spec.get().useDistance();
    }

    public boolean acceptsItem(ItemStack stack) {
        BiFunction<Object, Object, Boolean> filter = itemFilter.get();
        if (filter == null || stack == null || stack.isEmpty()) {
            return true;
        }
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return Boolean.TRUE.equals(com.luatweaker.platform.util.CallbackFirewall.supply(
                () -> filter.apply(itemId, stack.getCount()), Boolean.FALSE, LogStage.SYSTEM,
                () -> "Container itemFilter failed for " + blockId));
    }

    @Nullable
    public BooleanProperty getStateProperty() {
        return stateProperty;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        ContainerSpec current = this.spec != null ? this.spec.get() : INIT_SPEC.get();
        if (current == null) {
            return;
        }
        BooleanStateSpec stateSpec = current.booleanState();
        if (stateSpec != null && stateSpec.property() != null) {
            builder.add(BooleanProperty.create(stateSpec.property()));
        }
        if (current.connections()) {
            builder.add(PipeConnections.properties());
        }
    }

    @Override
    @NotNull
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PipeConnections.shape(state, spec.get().connections(), super.getShape(state, level, pos, context));
    }

    @Override
    @NotNull
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (hasCustomShape()) {
            return Shapes.empty();
        }
        return super.getOcclusionShape(state, level, pos);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return hasCustomShape();
    }

    private boolean hasCustomShape() {
        ContainerSpec current = spec.get();
        return current.connections() || current.geoModel() != null || !current.boxes().isEmpty();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context) != null
                ? super.getStateForPlacement(context)
                : defaultBlockState();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CustomContainerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || spec.get().tickHandler() == null) {
            return null;
        }
        net.minecraft.world.level.block.entity.BlockEntityType<?> ownType =
                CustomContainerRegistry.TYPE_BY_BLOCK.get(this);
        if (ownType == null || !Objects.equals(ownType, type)) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> CustomContainerBlockEntity.machineTick(
                tickLevel, tickPos, tickState, (CustomContainerBlockEntity) blockEntity);
    }

    // ===== Pipe connection maintenance =====

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide() && isConnected()) {
            PipeConnections.update(level, pos, state, this);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (!level.isClientSide() && isConnected()) {
            PipeConnections.update(level, pos, state, this);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!newState.is(state.getBlock()) && isConnected() && !level.isClientSide()) {
            PipeConnections.detachNeighbors(level, pos, this);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private boolean isConnected() {
        ContainerSpec current = spec.get();
        return current != null && current.connections();
    }

    @Override
    @NotNull
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof CustomContainerBlockEntity container)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        ContainerBlockActions.fireRightClick(rightClickHandler.get(), player, level, pos, blockId);
        if (!spec.get().isContainer()) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return ContainerBlockActions.openMenu(this, container, player, level, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity container) {
            ContainerBlockActions.dropContents(this, container, level, pos, spec.get());
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
