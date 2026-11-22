package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.container.CustomContainerBlock;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import com.luatweaker.common.container.CustomContainerMenu;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.common.container.CustomContainerScreen;
import com.luatweaker.common.container.ContainerSpec;
import com.luatweaker.common.util.CallbackFirewall;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registers Lua-declared blocks, their block items, block entities, and container menus.
 */
public final class BlockRegistrar {
    private static final String MOD_ID = "luatweaker";

    private final IContentService contentService;
    private final Map<ResourceLocation, Block> createdBlocks;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;
    private final Map<ResourceLocation, RegistrySupplier<Block>> blockHolders = new ConcurrentHashMap<>();

    public BlockRegistrar(IContentService contentService, Map<ResourceLocation, Block> createdBlocks,
                          Map<ResourceLocation, Item> createdItems,
                          Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdBlocks = createdBlocks;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    public void registerBlocks() {
        Registrar<Block> registrar = RegistrarManager.get(MOD_ID).get(Registries.BLOCK);
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            CallbackFirewall.run(
                    () -> registerOne(registrar, builder), LogStage.SYSTEM,
                    () -> "Failed to register block " + builder.getId());
        }
    }

    private void registerOne(Registrar<Block> registrar, IBlockBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        // Block creation touches self-registering vanilla holders
        // (SoundType/SoundEvents, ...), so it must run inside the deferred
        // supplier when the block registry is unfrozen - never here at
        // mod-construction time.
        RegistrySupplier<Block> holder =
                registrar.register(id, () -> {
                    Block block = BlockShapeFactory.create(builder, BlockPropertyFactory.create(builder), id);
                    createdBlocks.put(id, block);
                    CustomContainerRegistry.CREATED_BLOCKS.put(id, block);
                    registerHandlers(block, builder);
                    LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Block: " + id);
                    return block;
                });
        blockHolders.put(id, holder);
    }

    private static void registerHandlers(Block block, IBlockBuilder builder) {
        if (builder.getButtonClickHandler() != null) {
            CustomContainerRegistry.BUTTON_HANDLERS.put(
                    block, builder.getButtonClickHandler());
        }
        if (builder.getNeighborChangedHandler() != null) {
            CustomContainerRegistry.NEIGHBOR_HANDLERS.put(
                    block, builder.getNeighborChangedHandler());
        }
    }

    public void registerBlockItems() {
        Registrar<Item> registrar = RegistrarManager.get(MOD_ID).get(Registries.ITEM);
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            registerBlockItem(registrar, builder);
        }
    }

    private void registerBlockItem(Registrar<Item> registrar, IBlockBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        RegistrySupplier<Block> holder = blockHolders.get(id);
        if (holder == null) {
            Block vanilla = BuiltInRegistries.BLOCK.get(id);
            if (vanilla == Blocks.AIR) {
                return;
            }
            Item vanillaItem = new BlockItem(vanilla, new Item.Properties());
            createdItems.put(id, vanillaItem);
            registrar.register(id, () -> vanillaItem);
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockItem: " + id);
            return;
        }
        registrar.register(id, () -> {
            Block block = holder.get();
            Item blockItem = new BlockItem(block, new Item.Properties());
            createdItems.put(id, blockItem);
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockItem: " + id);
            return blockItem;
        });
    }

    @SuppressWarnings("unchecked")
    public void registerContainerBlockEntities() {
        Registrar<BlockEntityType<?>> registrar = RegistrarManager.get(MOD_ID).get(Registries.BLOCK_ENTITY_TYPE);
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            registerContainerBlockEntity(registrar, builder);
        }
    }

    private void registerContainerBlockEntity(Registrar<BlockEntityType<?>> registrar, IBlockBuilder builder) {
        if (!BlockPropertyFactory.hasBlockEntity(builder)) {
            return;
        }
        ResourceLocation id = locationParser.apply(builder.getId());
        RegistrySupplier<Block> holder = blockHolders.get(id);
        if (holder == null) {
            return;
        }
        registrar.register(id, () -> {
            Block resolved = holder.get();
            if (!(resolved instanceof CustomContainerBlock containerBlock)) {
                throw new IllegalStateException("Block '" + id + "' is not a container block");
            }
            var type = BlockEntityType.Builder.of(
                    CustomContainerBlockEntity::new, containerBlock).build(null);
            CustomContainerRegistry.TYPE_BY_BLOCK.put(containerBlock, type);
            CustomContainerRegistry.CONTAINER_BE_TYPES.put(id, type);
            CustomContainerRegistry.BLOCKS.put(id, containerBlock);
            CustomContainerRegistry.BLOCK_BY_TYPE.put(type, containerBlock);
            if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
                com.luatweaker.common.content.ContentRendererRegistrar.registerBlockRenderer(builder, type);
            }
            return type;
        });
    }

    public void registerContainerMenus() {
        Registrar<MenuType<?>> registrar = RegistrarManager.get(MOD_ID).get(Registries.MENU);
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if (!builder.isContainer() && !builder.isConnectionState()) {
                continue;
            }
            registerContainerMenu(registrar, builder);
        }
    }

    private void registerContainerMenu(Registrar<MenuType<?>> registrar, IBlockBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        var spec = ContainerSpec.from(builder);
        int slotCount = spec.rows() * spec.cols();
        var ref = new MenuType[1];
        ref[0] = new MenuType<>(
                (containerId, inventory) -> new CustomContainerMenu(
                        ref[0], containerId, inventory,
                        new SimpleContainer(slotCount), spec.rows(), spec.cols(),
                        spec.slotPositions(), spec.lockedSlots(), spec.manualSlots()),
                FeatureFlags.DEFAULT_FLAGS);
        var menuType = ref[0];
        CustomContainerRegistry.CONTAINER_MENUS.put(id, menuType);
        putIfPresent(CustomContainerRegistry.CONTAINER_TEXTURES,
                menuType, builder.getContainerTexture());
        putIfPresent(CustomContainerRegistry.CONTAINER_SLOT_TEXTURES,
                menuType, builder.getSlotTexture());
        if (!builder.getGuiBars().isEmpty()) {
            CustomContainerRegistry.CONTAINER_BARS.put(
                    menuType, builder.getGuiBars());
        }
        if (!builder.getButtons().isEmpty()) {
            CustomContainerRegistry.CONTAINER_BUTTONS.put(
                    menuType, builder.getButtons());
        }
        if (!builder.getLabels().isEmpty()) {
            CustomContainerRegistry.CONTAINER_LABELS.put(
                    menuType, builder.getLabels());
        }
        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
            dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(menuType, CustomContainerScreen::new);
        }
        registrar.register(id, () -> menuType);
    }

    private static <K, V> void putIfPresent(Map<K, V> target, K key, V value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
