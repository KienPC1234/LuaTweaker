package com.luatweaker.platform.container;

import com.luatweaker.api.content.BoxSpec;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.platform.content.VoxelShapeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class CustomBlock extends Block {
    protected final String blockId;
    protected final String facingMode;
    protected final Map<net.minecraft.core.Direction, VoxelShape> shapesByDirection;
    protected final VoxelShape defaultShape;
    @Nullable
    protected final AtomicReference<BiConsumer<Object, Object>> rightClickHandler;

    private static final ThreadLocal<String> INIT_FACING = new ThreadLocal<>();

    private static Properties pushFacing(Properties props, String facing) {
        INIT_FACING.set(FacingSupport.normalize(facing));
        return props;
    }

    public CustomBlock(Properties properties, String blockId, String facingMode,
                       List<BoxSpec> boxes, @Nullable BiConsumer<Object, Object> rightClickHandler) {
        this(pushFacing(properties, facingMode), blockId, facingMode, boxes, rightClickHandler, true);
    }

    private CustomBlock(Properties properties, String blockId, String facingMode,
                        List<BoxSpec> boxes, @Nullable BiConsumer<Object, Object> rightClickHandler,
                        boolean unused) {
        super(properties);
        INIT_FACING.remove();
        this.blockId = blockId;
        this.facingMode = FacingSupport.normalize(facingMode);
        this.shapesByDirection = VoxelShapeHelper.buildHorizontalShapes(boxes);
        this.defaultShape = VoxelShapeHelper.buildShape(boxes);
        this.rightClickHandler = new AtomicReference<>(rightClickHandler);
        registerDefaultState(FacingSupport.defaultState(this.stateDefinition.any(), this.facingMode));
    }

    public String getBlockId() {
        return blockId;
    }

    /** Replaces the right-click callback after {@code /lt reload}. */
    public void setRightClickHandler(@Nullable BiConsumer<Object, Object> handler) {
        rightClickHandler.set(handler);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (level.isClientSide()) {
            return;
        }
        BiConsumer<Object, Object> handler = CustomContainerRegistry.NEIGHBOR_HANDLERS.get(this);
        if (handler == null) {
            return;
        }
        com.luatweaker.platform.util.CallbackFirewall.run(() -> handler.accept(
                        Map.of("X", pos.getX(), "Y", pos.getY(), "Z", pos.getZ()),
                        Map.of("X", neighborPos.getX(), "Y", neighborPos.getY(), "Z", neighborPos.getZ())),
                LogStage.SYSTEM,
                () -> "Failed neighborChanged handler for " + blockId);
    }

    public String getFacingMode() {
        return facingMode;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        FacingSupport.addProperty(builder, resolveFacingMode());
    }

    private String resolveFacingMode() {
        return this.facingMode != null ? this.facingMode : INIT_FACING.get();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FacingSupport.placementState(defaultBlockState(), facingMode, context);
    }

    @Override
    @NotNull
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (FacingSupport.isHorizontal(facingMode) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return shapesByDirection.getOrDefault(state.getValue(BlockStateProperties.HORIZONTAL_FACING),
                    defaultShape);
        }
        return defaultShape;
    }

    @Override
    @NotNull
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    @NotNull
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // buildHorizontalShapes always returns a populated map, so testing !isEmpty()
        // would make every custom block non-occluding. Only a genuinely custom shape
        // (not the full-block default) should let neighbours render their faces.
        if (!java.util.Objects.equals(defaultShape, net.minecraft.world.phys.shapes.Shapes.block())) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        return super.getOcclusionShape(state, level, pos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return FacingSupport.rotate(state, facingMode, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return FacingSupport.mirror(state, facingMode, mirror);
    }

    @Override
    @NotNull
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hitResult) {
        if (level.isClientSide() || rightClickHandler.get() == null) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        ContainerBlockActions.fireRightClick(rightClickHandler.get(), player, level, pos, blockId);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}


