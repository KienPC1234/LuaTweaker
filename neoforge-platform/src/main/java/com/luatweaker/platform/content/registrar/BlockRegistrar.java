package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.function.Function;

public final class BlockRegistrar {
    private final IContentService contentService;
    private final Map<ResourceLocation, Block> createdBlocks;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;

    public BlockRegistrar(IContentService contentService, Map<ResourceLocation, Block> createdBlocks, Map<ResourceLocation, Item> createdItems, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdBlocks = createdBlocks;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    public void registerBlocks(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .strength((float) builder.getHardness(), (float) builder.getResistance())
                        .lightLevel(state -> builder.getLightLevel());

                if (builder.getSoundType() != null) {
                    props.sound(parseSoundType(builder.getSoundType()));
                }
                if (builder.getFriction() != 0.6f) {
                    props.friction(builder.getFriction());
                }
                if (builder.getRequiresTool() || builder.getMiningLevel() > 0 || builder.getMineableWith() != null) {
                    props.requiresCorrectToolForDrops();
                }

                String id = builder.getId().toLowerCase();
                Block block;
                if (id.endsWith("_stairs") || id.contains("stairs")) {
                    block = new StairBlock(Blocks.STONE.defaultBlockState(), props) {
                        @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                try {
                                    builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), null);
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed block right-click handler for " + builder.getId() + ": " + e.getMessage());
                                }
                                return InteractionResult.sidedSuccess(level.isClientSide());
                            }
                            return super.useWithoutItem(state, level, pos, player, hitResult);
                        }
                    };
                } else if (id.endsWith("_slab") || id.contains("slab")) {
                    block = new SlabBlock(props) {
                        @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                try {
                                    builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), null);
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed block right-click handler for " + builder.getId() + ": " + e.getMessage());
                                }
                                return InteractionResult.sidedSuccess(level.isClientSide());
                            }
                            return super.useWithoutItem(state, level, pos, player, hitResult);
                        }
                    };
                } else if (id.endsWith("_wall") || id.contains("wall")) {
                    block = new WallBlock(props) {
                        @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                try {
                                    builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), null);
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed block right-click handler for " + builder.getId() + ": " + e.getMessage());
                                }
                                return InteractionResult.sidedSuccess(level.isClientSide());
                            }
                            return super.useWithoutItem(state, level, pos, player, hitResult);
                        }
                    };
                } else {
                    block = new Block(props) {
                        @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                try {
                                    builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), null);
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed block right-click handler for " + builder.getId() + ": " + e.getMessage());
                                }
                                return InteractionResult.sidedSuccess(level.isClientSide());
                            }
                            return super.useWithoutItem(state, level, pos, player, hitResult);
                        }
                    };
                }

                event.register(Registries.BLOCK, rl, () -> block);
                createdBlocks.put(rl, block);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Block to NeoForge: " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register block " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public void registerBlockItems(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            ResourceLocation rl = locationParser.apply(builder.getId());
            Block block = createdBlocks.get(rl);
            if (block == null) {
                block = BuiltInRegistries.BLOCK.get(rl);
            }
            if (block != null && block != Blocks.AIR) {
                Block finalBlock = block;
                Item blockItem = new BlockItem(finalBlock, new Item.Properties());
                createdItems.put(rl, blockItem);
                event.register(Registries.ITEM, rl, () -> blockItem);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockItem to NeoForge: " + rl);
            }
        }
    }

    private net.minecraft.world.level.block.SoundType parseSoundType(String s) {
        if (s == null) return net.minecraft.world.level.block.SoundType.STONE;
        return switch (s.toUpperCase()) {
            case "WOOD" -> net.minecraft.world.level.block.SoundType.WOOD;
            case "GRAVEL" -> net.minecraft.world.level.block.SoundType.GRAVEL;
            case "METAL" -> net.minecraft.world.level.block.SoundType.METAL;
            case "GLASS" -> net.minecraft.world.level.block.SoundType.GLASS;
            case "GRASS" -> net.minecraft.world.level.block.SoundType.GRASS;
            case "SAND" -> net.minecraft.world.level.block.SoundType.SAND;
            case "SNOW" -> net.minecraft.world.level.block.SoundType.SNOW;
            case "WOOL" -> net.minecraft.world.level.block.SoundType.WOOL;
            case "ANVIL" -> net.minecraft.world.level.block.SoundType.ANVIL;
            case "NETHERITE" -> net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK;
            default -> net.minecraft.world.level.block.SoundType.STONE;
        };
    }
}
