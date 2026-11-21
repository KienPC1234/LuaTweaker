package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.function.Function;

/**
 * Registers Lua-declared blocks, their block items and container menus.
 *
 * <p>Creation lives in {@link BlockShapeFactory}/{@link BlockPropertyFactory}; this class only
 * maps each builder onto the registry events.
 */
public final class BlockRegistrar {
    private final IContentService contentService;
    private final Map<ResourceLocation, Block> createdBlocks;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;

    public BlockRegistrar(IContentService contentService, Map<ResourceLocation, Block> createdBlocks,
                          Map<ResourceLocation, Item> createdItems,
                          Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdBlocks = createdBlocks;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    public void registerBlocks(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            com.luatweaker.platform.util.CallbackFirewall.run(
                    () -> registerOne(event, builder), LogStage.SYSTEM,
                    () -> "Failed to register block " + builder.getId());
        }
    }

    private void registerOne(RegisterEvent event, IBlockBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        Block block = BlockShapeFactory.create(builder, BlockPropertyFactory.create(builder), id);
        Block registered = block;
        event.register(Registries.BLOCK, id, () -> registered);
        createdBlocks.put(id, block);
        com.luatweaker.platform.container.CustomContainerRegistry.CREATED_BLOCKS.put(id, block);
        registerHandlers(block, builder);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Block to NeoForge: " + id);
    }

    private static void registerHandlers(Block block, IBlockBuilder builder) {
        if (builder.getButtonClickHandler() != null) {
            com.luatweaker.platform.container.CustomContainerRegistry.BUTTON_HANDLERS.put(
                    block, builder.getButtonClickHandler());
        }
        if (builder.getNeighborChangedHandler() != null) {
            com.luatweaker.platform.container.CustomContainerRegistry.NEIGHBOR_HANDLERS.put(
                    block, builder.getNeighborChangedHandler());
        }
    }

    public void registerBlockItems(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            registerBlockItem(event, builder);
        }
    }

    private void registerBlockItem(RegisterEvent event, IBlockBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        Block block = createdBlocks.get(id);
        if (block == null) {
            block = BuiltInRegistries.BLOCK.get(id);
        }
        if (block == Blocks.AIR) {
            return;
        }
        Block registered = block;
        Item blockItem = new BlockItem(registered, new Item.Properties());
        createdItems.put(id, blockItem);
        event.register(Registries.ITEM, id, () -> blockItem);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockItem to NeoForge: " + id);
    }

    @SuppressWarnings("unchecked")
    public void registerContainerBlockEntities(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            registerContainerBlockEntity(event, builder);
        }
    }

    private void registerContainerBlockEntity(RegisterEvent event, IBlockBuilder builder) {
        if (!BlockPropertyFactory.hasBlockEntity(builder)) {
            return;
        }
        ResourceLocation id = locationParser.apply(builder.getId());
        Block block = createdBlocks.get(id);
        if (block == null) {
            block = BuiltInRegistries.BLOCK.get(id);
        }
        if (block == Blocks.AIR) {
            return;
        }
        var containerBlock = (com.luatweaker.platform.container.CustomContainerBlock) block;
        var type = net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                com.luatweaker.platform.container.CustomContainerBlockEntity::new, block).build(null);
        com.luatweaker.platform.container.CustomContainerRegistry.TYPE_BY_BLOCK.put(containerBlock, type);
        com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BE_TYPES.put(id, type);
        com.luatweaker.platform.container.CustomContainerRegistry.BLOCKS.put(id, containerBlock);
        com.luatweaker.platform.container.CustomContainerRegistry.BLOCK_BY_TYPE.put(type, containerBlock);
        event.register(Registries.BLOCK_ENTITY_TYPE, id, () -> type);
    }

    public void registerContainerMenus(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if (!builder.isContainer() && !builder.isConnectionState()) {
                continue;
            }
            registerContainerMenu(event, builder);
        }
    }

    private void registerContainerMenu(RegisterEvent event, IBlockBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        var spec = com.luatweaker.platform.container.ContainerSpec.from(builder);
        int slotCount = spec.rows() * spec.cols();
        var ref = new net.minecraft.world.inventory.MenuType[1];
        ref[0] = new net.minecraft.world.inventory.MenuType<>(
                (containerId, inventory) -> new com.luatweaker.platform.container.CustomContainerMenu(
                        ref[0], containerId, inventory,
                        new net.minecraft.world.SimpleContainer(slotCount), spec.rows(), spec.cols()),
                net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        var menuType = ref[0];
        com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_MENUS.put(id, menuType);
        putIfPresent(com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_TEXTURES,
                menuType, builder.getContainerTexture());
        putIfPresent(com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_SLOT_TEXTURES,
                menuType, builder.getSlotTexture());
        if (!builder.getGuiBars().isEmpty()) {
            com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BARS.put(
                    menuType, builder.getGuiBars());
        }
        if (!builder.getButtons().isEmpty()) {
            com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BUTTONS.put(
                    menuType, builder.getButtons());
        }
        if (!builder.getLabels().isEmpty()) {
            com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_LABELS.put(
                    menuType, builder.getLabels());
        }
        event.register(Registries.MENU, id, () -> menuType);
    }

    private static <K, V> void putIfPresent(Map<K, V> target, K key, V value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}

