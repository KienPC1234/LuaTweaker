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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Lua-configured container or functional machine block.
 * Supports inventory grids, FE energy, fluid storage, per-tick handlers,
 * custom voxel bounds, placement facing, and BER rendering.
 */
public class CustomContainerBlock extends CustomBlock implements EntityBlock {

    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    public static final BooleanProperty[] CONNECTION_PROPS = {
            BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH,
            BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST
    };

    public static BooleanProperty getConnectionProp(Direction dir) {
        return switch (dir) {
            case DOWN -> BlockStateProperties.DOWN;
            case UP -> BlockStateProperties.UP;
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case EAST -> BlockStateProperties.EAST;
        };
    }

    private static final VoxelShape PIPE_CORE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
    private static final VoxelShape[] PIPE_ARMS = {
            Block.box(4.0, 0.0, 4.0, 12.0, 4.0, 12.0), // DOWN
            Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0), // UP
            Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 4.0), // NORTH
            Block.box(4.0, 4.0, 12.0, 12.0, 12.0, 16.0), // SOUTH
            Block.box(0.0, 4.0, 4.0, 4.0, 12.0, 12.0), // WEST
            Block.box(12.0, 4.0, 4.0, 16.0, 12.0, 12.0)  // EAST
    };

    private static final ThreadLocal<ContainerSpec> INIT_SPEC = new ThreadLocal<>();

    private static Properties pushSpec(Properties props, ContainerSpec spec) {
        INIT_SPEC.set(spec);
        return props;
    }

    private final String crateTitle;
    /** Replaced on {@code /lt reload}; read every tick/frame, hence volatile. */
    private volatile ContainerSpec spec;
    private volatile java.util.function.BiFunction<Object, Object, Boolean> itemFilter;
    @Nullable
    private final BooleanProperty stateProperty;

    public CustomContainerBlock(Properties properties, String crateId, String crateTitle,
                                ContainerSpec spec,
                                @Nullable BiConsumer<Object, Object> rightClickHandler,
                                @Nullable java.util.function.BiFunction<Object, Object, Boolean> itemFilter) {
        this(pushSpec(properties, spec), crateId, crateTitle, spec, rightClickHandler, itemFilter, true);
    }

    private CustomContainerBlock(Properties properties, String crateId, String crateTitle,
                                 ContainerSpec spec,
                                 @Nullable BiConsumer<Object, Object> rightClickHandler,
                                 @Nullable java.util.function.BiFunction<Object, Object, Boolean> itemFilter,
                                 boolean unused) {
        super(properties, crateId, spec != null ? spec.facing() : null, spec != null ? spec.boxes() : java.util.List.of(), rightClickHandler);
        INIT_SPEC.remove();
        this.crateTitle = crateTitle;
        this.spec = spec;
        this.itemFilter = itemFilter;
        BooleanStateSpec stateSpec = spec != null ? spec.booleanState() : null;
        this.stateProperty = (stateSpec != null && stateSpec.property() != null)
                ? (BooleanProperty) this.getStateDefinition().getProperty(stateSpec.property())
                : null;

        BlockState defaultState = defaultBlockState();
        if (stateProperty != null) {
            defaultState = defaultState.setValue(stateProperty, false);
        }
        if (spec != null && spec.connections()) {
            for (BooleanProperty p : CONNECTION_PROPS) {
                defaultState = defaultState.setValue(p, false);
            }
        }
        registerDefaultState(defaultState);
    }

    public String getContainerId() {
        return blockId;
    }

    public String getContainerTitle() {
        return crateTitle;
    }

    @NotNull
    public ContainerSpec getSpec() {
        return spec;
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        if (spec.geoModel() != null) {
            return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
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
     * @param refreshed       spec rebuilt from the current builder
     * @param newItemFilter   current {@code :ItemFilter} callback (may be null)
     * @param newRightClick   current {@code :OnRightClick} callback (may be null)
     * @return true when the block behaviour was replaced
     */
    public boolean rebind(ContainerSpec refreshed,
                          @Nullable java.util.function.BiFunction<Object, Object, Boolean> newItemFilter,
                          @Nullable BiConsumer<Object, Object> newRightClick) {
        if (refreshed == null) {
            return false;
        }
        ContainerSpec previous = this.spec;
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
        this.spec = ContainerSpec.adoptRuntime(previous, refreshed);
        this.itemFilter = newItemFilter;
        setRightClickHandler(newRightClick);
        return true;
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

    public double getUseDistance() {
        return spec.useDistance();
    }

    public boolean acceptsItem(ItemStack stack) {
        if (itemFilter == null || stack == null || stack.isEmpty()) return true;
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try {
            return Boolean.TRUE.equals(itemFilter.apply(itemId, stack.getCount()));
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "Container itemFilter failed for " + blockId + ": " + e.getMessage());
            return false;
        }
    }

    @Nullable
    public BooleanProperty getStateProperty() {
        return stateProperty;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        ContainerSpec s = this.spec != null ? this.spec : INIT_SPEC.get();
        if (s != null) {
            BooleanStateSpec stateSpec = s.booleanState();
            if (stateSpec != null && stateSpec.property() != null) {
                builder.add(BooleanProperty.create(stateSpec.property()));
            }
            if (s.connections()) {
                builder.add(CONNECTION_PROPS);
            }
        }
    }

    @Override
    @NotNull
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (spec != null && spec.connections()) {
            VoxelShape shape = PIPE_CORE;
            for (int i = 0; i < CONNECTION_PROPS.length; i++) {
                if (state.hasProperty(CONNECTION_PROPS[i]) && Boolean.TRUE.equals(state.getValue(CONNECTION_PROPS[i]))) {
                    shape = Shapes.or(shape, PIPE_ARMS[i]);
                }
            }
            return shape;
        }
        return super.getShape(state, level, pos, context);
    }

    @Override
    @NotNull
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (spec != null && (spec.connections() || spec.geoModel() != null || !spec.boxes().isEmpty())) {
            return Shapes.empty();
        }
        return super.getOcclusionShape(state, level, pos);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return spec != null && (spec.connections() || spec.geoModel() != null || !spec.boxes().isEmpty());
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            state = defaultBlockState();
        }
        return state;
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
        if (!oldState.is(state.getBlock()) && !level.isClientSide() && spec != null && spec.connections()) {
            updateConnections(level, pos, state);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (!level.isClientSide() && spec != null && spec.connections()) {
            updateConnections(level, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!newState.is(state.getBlock()) && spec != null && spec.connections() && !level.isClientSide()) {
            for (Direction dir : ALL_DIRECTIONS) {
                BlockPos neighbor = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighbor);
                BooleanProperty oppositeProp = getConnectionProp(dir.getOpposite());
                if (neighborState.getBlock() == this && neighborState.hasProperty(oppositeProp) && Boolean.TRUE.equals(neighborState.getValue(oppositeProp))) {
                    level.setBlock(neighbor, neighborState.setValue(oppositeProp, false), 3);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void updateConnections(Level level, BlockPos pos, BlockState currentState) {
        if (spec == null || !spec.connections()) return;
        BlockState updated = currentState;
        for (Direction dir : ALL_DIRECTIONS) {
            BooleanProperty prop = getConnectionProp(dir);
            if (!updated.hasProperty(prop)) continue;
            BlockPos neighbor = pos.relative(dir);
            boolean connects = level.getBlockState(neighbor).getBlock() == this;
            updated = updated.setValue(prop, connects);
        }
        if (updated != currentState) {
            level.setBlock(pos, updated, 3);
        }
    }

    @Override
    @NotNull
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity container) {
            if (rightClickHandler != null) {
                try {
                    String dim = level.dimension().location().toString();
                    var blockWrapper = new com.luatweaker.platform.interaction.NeoForgeInteractableBlock(dim, pos.getX(), pos.getY(), pos.getZ());
                    rightClickHandler.accept(new NeoForgePlayerWrapper(player), blockWrapper);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed container right-click handler for " + blockId + ": " + e.getMessage());
                }
            }
            if (!spec.isContainer()) {
                return InteractionResult.sidedSuccess(level.isClientSide());
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
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
