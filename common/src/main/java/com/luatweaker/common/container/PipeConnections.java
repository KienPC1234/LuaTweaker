package com.luatweaker.common.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/** Pipe connection blockstate maintenance and shape for {@link CustomContainerBlock}. */
final class PipeConnections {
    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final BooleanProperty[] PROPS = {
            BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH,
            BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST
    };
    private static final VoxelShape PIPE_CORE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
    private static final VoxelShape[] PIPE_ARMS = {
            Block.box(4.0, 0.0, 4.0, 12.0, 4.0, 12.0),
            Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0),
            Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 4.0),
            Block.box(4.0, 4.0, 12.0, 12.0, 12.0, 16.0),
            Block.box(0.0, 4.0, 4.0, 4.0, 12.0, 12.0),
            Block.box(12.0, 4.0, 4.0, 16.0, 12.0, 12.0)
    };

    private PipeConnections() {
    }

    /** The six connection properties, in {@link Direction#values()} order. */
    static BooleanProperty[] properties() {
        return PROPS.clone();
    }

    static BooleanProperty property(Direction direction) {
        return PROPS[direction.ordinal()];
    }

    /** Recomputes this block's connection properties from its neighbors. */
    static void update(Level level, BlockPos pos, BlockState currentState, Block self) {
        BlockState updated = currentState;
        for (Direction direction : ALL_DIRECTIONS) {
            BooleanProperty property = property(direction);
            if (!updated.hasProperty(property)) {
                continue;
            }
            BlockPos neighbor = pos.relative(direction);
            boolean connects = Objects.equals(level.getBlockState(neighbor).getBlock(), self);
            updated = updated.setValue(property, connects);
        }
        if (!Objects.equals(updated, currentState)) {
            level.setBlock(pos, updated, 3);
        }
    }

    /** Clears the facing connection property on every adjacent pipe when this block is removed. */
    static void detachNeighbors(Level level, BlockPos pos, Block self) {
        for (Direction direction : ALL_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighbor);
            BooleanProperty opposite = property(direction.getOpposite());
            if (Objects.equals(neighborState.getBlock(), self)
                    && neighborState.hasProperty(opposite)
                    && Boolean.TRUE.equals(neighborState.getValue(opposite))) {
                level.setBlock(neighbor, neighborState.setValue(opposite, false), 3);
            }
        }
    }

    /** Pipe shape when connections are enabled, else {@code fallback}. */
    static VoxelShape shape(BlockState state, boolean connections, VoxelShape fallback) {
        if (!connections) {
            return fallback;
        }
        VoxelShape shape = PIPE_CORE;
        for (int index = 0; index < PROPS.length; index++) {
            if (state.hasProperty(PROPS[index]) && Boolean.TRUE.equals(state.getValue(PROPS[index]))) {
                shape = Shapes.or(shape, PIPE_ARMS[index]);
            }
        }
        return shape;
    }
}
