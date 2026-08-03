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

                // Lua-configured inventory container: use the generic crate block.
                // MUST be decided before the plain-block branch: every Block
                // constructor creates an intrusive registry holder, and any
                // instance that never gets registered crashes registry freeze.
                Block block;
                if (builder.isContainer()) {
                    String crateTitle = builder.getContainerTitle() != null && !builder.getContainerTitle().isBlank()
                            ? builder.getContainerTitle() : toTitle(rl.getPath());
                    block = new com.luatweaker.platform.crate.ContainerCrateBlock(
                            props, rl.toString(), crateTitle, builder.getContainerRows(), builder.getContainerCols(),
                            builder.getContainerDropMode(), builder.getContainerTexture(),
                            builder.getRightClickHandler(), builder.getItemFilter());
                    LuaTweakerLog.get().info(LogStage.SYSTEM,
                            "Registered container block '" + id + "' (" + builder.getContainerRows() + "x"
                                    + builder.getContainerCols() + ", drop=" + builder.getContainerDropMode() + ")");
                } else if (id.endsWith("_stairs") || id.contains("stairs")) {
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

                Block finalBlock = block;
                event.register(Registries.BLOCK, rl, () -> finalBlock);
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

    public void registerContainerBlockEntities(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if (!builder.isContainer()) continue;
            ResourceLocation rl = locationParser.apply(builder.getId());
            Block block = createdBlocks.get(rl);
            if (block == null) block = BuiltInRegistries.BLOCK.get(rl);
            if (block == null || block == Blocks.AIR) continue;
            net.minecraft.world.level.block.entity.BlockEntityType<com.luatweaker.platform.crate.ContainerCrateBlockEntity> type =
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            com.luatweaker.platform.crate.ContainerCrateBlockEntity::new, block).build(null);
            com.luatweaker.platform.crate.ContainerCrateRegistry.TYPE_BY_BLOCK.put(
                    (com.luatweaker.platform.crate.ContainerCrateBlock) block, type);
            com.luatweaker.platform.crate.ContainerCrateRegistry.CRATE_BE_TYPES.put(rl, type);
            event.register(Registries.BLOCK_ENTITY_TYPE, rl, () -> type);
        }
    }

    public void registerContainerMenus(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if (!builder.isContainer()) continue;
            ResourceLocation rl = locationParser.apply(builder.getId());
            int rows = builder.getContainerRows();
            int cols = builder.getContainerCols();
            int slotCount = rows * cols;
            net.minecraft.world.inventory.MenuType<com.luatweaker.platform.crate.ContainerCrateMenu>[] ref =
                    new net.minecraft.world.inventory.MenuType[1];
            ref[0] = new net.minecraft.world.inventory.MenuType<>((containerId, inventory) ->
                            new com.luatweaker.platform.crate.ContainerCrateMenu(
                                    ref[0], containerId, inventory, new net.minecraft.world.SimpleContainer(slotCount), rows, cols),
                            net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
            net.minecraft.world.inventory.MenuType<com.luatweaker.platform.crate.ContainerCrateMenu> menuType = ref[0];
            com.luatweaker.platform.crate.ContainerCrateRegistry.CRATE_MENUS.put(rl, menuType);
            com.luatweaker.platform.crate.ContainerCrateRegistry.CRATE_TEXTURES.put(
                    menuType, builder.getContainerTexture());
            event.register(Registries.MENU, rl, () -> menuType);
        }
    }

    private static String toTitle(String id) {
        String clean = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        if (clean.isEmpty()) return id;
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
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
