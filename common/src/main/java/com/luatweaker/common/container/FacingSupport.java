package com.luatweaker.common.container;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

import java.util.Locale;

/** Shared placement/facing blockstate handling for Lua blocks. */
final class FacingSupport {
    static final String MODE_NONE = "NONE";
    static final String MODE_HORIZONTAL = "HORIZONTAL";
    static final String MODE_DIRECTIONAL = "DIRECTIONAL";
    static final String MODE_SIX_WAY = "6WAY";

    private FacingSupport() {
    }

    static String normalize(String mode) {
        return mode != null ? mode.toUpperCase(Locale.ROOT) : MODE_NONE;
    }

    static boolean isHorizontal(String mode) {
        return MODE_HORIZONTAL.equals(mode);
    }

    static boolean isDirectional(String mode) {
        return MODE_DIRECTIONAL.equals(mode) || MODE_SIX_WAY.equals(mode);
    }

    static void addProperty(StateDefinition.Builder<Block, BlockState> builder, String mode) {
        if (isHorizontal(mode)) {
            builder.add(BlockStateProperties.HORIZONTAL_FACING);
        } else if (isDirectional(mode)) {
            builder.add(BlockStateProperties.FACING);
        }
    }

    static BlockState defaultState(BlockState state, String mode) {
        if (isHorizontal(mode)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        if (isDirectional(mode)) {
            return state.setValue(BlockStateProperties.FACING, Direction.NORTH);
        }
        return state;
    }

    static BlockState placementState(BlockState state, String mode, BlockPlaceContext context) {
        if (isHorizontal(mode)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    context.getHorizontalDirection().getOpposite());
        }
        if (isDirectional(mode)) {
            return state.setValue(BlockStateProperties.FACING,
                    context.getNearestLookingDirection().getOpposite());
        }
        return state;
    }

    static BlockState rotate(BlockState state, String mode, Rotation rotation) {
        if (isHorizontal(mode) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        if (isDirectional(mode) && state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING,
                    rotation.rotate(state.getValue(BlockStateProperties.FACING)));
        }
        return state;
    }

    static BlockState mirror(BlockState state, String mode, Mirror mirror) {
        if (isHorizontal(mode) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    mirror.mirror(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        if (isDirectional(mode) && state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING,
                    mirror.mirror(state.getValue(BlockStateProperties.FACING)));
        }
        return state;
    }
}
