package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Creates the concrete block instance (shape/container/plain) for a Lua block builder. */
final class BlockShapeFactory {
    private static final String SHAPE_STAIRS = "STAIRS";
    private static final String SHAPE_SLAB = "SLAB";
    private static final String SHAPE_WALL = "WALL";

    private BlockShapeFactory() {
    }

    static Block create(IBlockBuilder builder, BlockBehaviour.Properties props, ResourceLocation id) {
        if (BlockPropertyFactory.hasBlockEntity(builder)) {
            return container(builder, props, id);
        }
        String shape = builder.getEffectiveShape();
        if (SHAPE_STAIRS.equals(shape)) {
            return stairs(builder, props);
        }
        if (SHAPE_SLAB.equals(shape)) {
            return slab(builder, props);
        }
        if (SHAPE_WALL.equals(shape)) {
            return wall(builder, props);
        }
        if (!builder.getBoxes().isEmpty()
                || (builder.getFacing() != null && !"NONE".equalsIgnoreCase(builder.getFacing()))) {
            return customShape(builder, props, id);
        }
        return plain(builder, props);
    }



    private static Block container(IBlockBuilder builder, BlockBehaviour.Properties props, ResourceLocation id) {
        var spec = com.luatweaker.platform.container.ContainerSpec.from(builder);
        String title = builder.getContainerTitle() != null && !builder.getContainerTitle().isBlank()
                ? builder.getContainerTitle()
                : toTitle(id.getPath());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered container block '" + id.toString().toLowerCase(Locale.ROOT) + "' ("
                        + spec.rows() + "x" + spec.cols() + ", drop=" + spec.dropMode()
                        + ", useDistance=" + builder.getContainerUseDistance()
                        + ", lockedSlots=" + builder.getLockedSlots().size()
                        + ", customSlots=" + builder.getSlotPositions().size()
                        + ", energy=" + builder.getEnergyCapacity()
                        + ", fluid=" + builder.getFluidCapacity()
                        + ", bars=" + builder.getGuiBars().size()
                        + ", state=" + (builder.getBooleanState() != null
                        ? builder.getBooleanState().property() : "none")
                        + ", connections=" + builder.isConnectionState()
                        + ", tick=" + (builder.getTickHandler() != null)
                        + ", render=" + (builder.getRenderHandler() != null) + ")");
        return new com.luatweaker.platform.container.CustomContainerBlock(
                props, id.toString(), title, spec, builder.getRightClickHandler(), builder.getItemFilter());
    }

    private static Block stairs(IBlockBuilder builder, BlockBehaviour.Properties props) {
        BlockState baseState = resolveBaseState(builder);
        return createBlockWithHook(builder, props, (p, b) -> new StairBlock(baseState, p) {
            @Override
            public InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                    net.minecraft.core.BlockPos pos,
                                                    net.minecraft.world.entity.player.Player player,
                                                    net.minecraft.world.phys.BlockHitResult hitResult) {
                InteractionResult result = BlockUseHandler.handleUse(level, pos, player, b);
                return result != null ? result : super.useWithoutItem(state, level, pos, player, hitResult);
            }
        });
    }

    private static Block slab(IBlockBuilder builder, BlockBehaviour.Properties props) {
        return createBlockWithHook(builder, props, (p, b) -> new SlabBlock(p) {
            @Override
            public InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                    net.minecraft.core.BlockPos pos,
                                                    net.minecraft.world.entity.player.Player player,
                                                    net.minecraft.world.phys.BlockHitResult hitResult) {
                InteractionResult result = BlockUseHandler.handleUse(level, pos, player, b);
                return result != null ? result : super.useWithoutItem(state, level, pos, player, hitResult);
            }
        });
    }

    private static Block wall(IBlockBuilder builder, BlockBehaviour.Properties props) {
        return createBlockWithHook(builder, props, (p, b) -> new WallBlock(p) {
            @Override
            public InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                    net.minecraft.core.BlockPos pos,
                                                    net.minecraft.world.entity.player.Player player,
                                                    net.minecraft.world.phys.BlockHitResult hitResult) {
                InteractionResult result = BlockUseHandler.handleUse(level, pos, player, b);
                return result != null ? result : super.useWithoutItem(state, level, pos, player, hitResult);
            }
        });
    }

    private static Block customShape(IBlockBuilder builder, BlockBehaviour.Properties props, ResourceLocation id) {
        // CustomBlock supplies the shape/facing overrides; the use hook needs the real
        // level/pos from the interaction, which only the override provides.
        return createBlockWithHook(builder, props, (p, b) ->
                new com.luatweaker.platform.container.CustomBlock(
                        p, id.toString(), builder.getFacing(), builder.getBoxes(), null) {
                    @Override
                    public InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                            net.minecraft.core.BlockPos pos,
                                                            net.minecraft.world.entity.player.Player player,
                                                            net.minecraft.world.phys.BlockHitResult hitResult) {
                        InteractionResult result = BlockUseHandler.handleUse(level, pos, player, b);
                        return result != null
                                ? result
                                : super.useWithoutItem(state, level, pos, player, hitResult);
                    }
                });
    }

    private static Block plain(IBlockBuilder builder, BlockBehaviour.Properties props) {
        return createBlockWithHook(builder, props, (p, b) -> new Block(p) {
            @Override
            public InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                    net.minecraft.core.BlockPos pos,
                                                    net.minecraft.world.entity.player.Player player,
                                                    net.minecraft.world.phys.BlockHitResult hitResult) {
                InteractionResult result = BlockUseHandler.handleUse(level, pos, player, b);
                return result != null ? result : super.useWithoutItem(state, level, pos, player, hitResult);
            }
        });
    }

    private static Block createBlockWithHook(IBlockBuilder builder, BlockBehaviour.Properties props,
                                             BlockFactory factory) {
        return factory.create(props, builder);
    }

    static String toTitle(String id) {
        String clean = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        if (clean.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    /** Base state backing a stair block; falls back to stone when the base is not registered. */
    private static BlockState resolveBaseState(IBlockBuilder builder) {
        String baseId = builder.getBaseBlockId();
        if (baseId == null) {
            return Blocks.STONE.defaultBlockState();
        }
        ResourceLocation baseRl = ResourceLocation.tryParse(baseId);
        Block base = baseRl != null ? BuiltInRegistries.BLOCK.get(baseRl) : Blocks.AIR;
        if (base != Blocks.AIR) {
            return base.defaultBlockState();
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "Stairs '" + builder.getId() + "': base block '" + baseId
                        + "' is not registered yet; the stair base state falls back to stone");
        return Blocks.STONE.defaultBlockState();
    }

    @FunctionalInterface
    interface BlockFactory {
        Block create(BlockBehaviour.Properties props, IBlockBuilder builder);
    }
}


