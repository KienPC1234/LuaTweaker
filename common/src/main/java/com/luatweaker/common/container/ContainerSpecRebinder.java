package com.luatweaker.common.container;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Re-attaches Lua block behaviour to the freshly created engine after {@code /lt reload}.
 *
 * <p>NeoForge registries (block, block entity, capability, menu, renderer) are frozen once
 * the game starts, so the registered objects cannot be replaced. The Lua closures they use,
 * however, can: this pass rebuilds each {@link ContainerSpec} from the builder the startup
 * scripts created on the new VM and adopts the runtime parts. Without it, every
 * {@code :OnTick}, {@code :ItemFilter}, {@code :OnRightClick}, {@code :OnButtonClick} and
 * {@code :OnNeighborChanged} kept calling a shut-down engine after the first reload.</p>
 */
public final class ContainerSpecRebinder {

    private ContainerSpecRebinder() {
    }

    /**
     * @param contentService the long-lived content service the startup scripts re-populate
     * @param idParser       the same id normalizer used when the blocks were registered
     * @return how many registered blocks were refreshed
     */
    public static int rebindAll(IContentService contentService,
                                Function<String, ResourceLocation> idParser) {
        if (contentService == null || idParser == null) {
            return 0;
        }
        var builders = new HashMap<ResourceLocation, IBlockBuilder>();
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            builders.put(idParser.apply(builder.getId()), builder);
        }

        int rebound = 0;
        for (Map.Entry<ResourceLocation, Block> entry : CustomContainerRegistry.CREATED_BLOCKS.entrySet()) {
            IBlockBuilder builder = builders.get(entry.getKey());
            if (builder == null) {
                // A mod that no longer declares the block keeps working until restart; the
                // registry still holds it, so there is nothing to unbind safely.
                continue;
            }
            Block block = entry.getValue();
            putOrRemove(CustomContainerRegistry.BUTTON_HANDLERS, block, builder.getButtonClickHandler());
            putOrRemove(CustomContainerRegistry.NEIGHBOR_HANDLERS, block, builder.getNeighborChangedHandler());

            if (block instanceof CustomContainerBlock container) {
                container.rebind(ContainerSpec.from(builder), builder.getItemFilter(),
                        builder.getRightClickHandler());
                rebindMenu(entry.getKey(), builder);
            }
            rebound++;
        }
        LuaTweakerLog.get().info(LogStage.RELOAD,
                "[Content] Rebound " + rebound + " block(s) to the reloaded Lua VM");
        return rebound;
    }

    private static void rebindMenu(ResourceLocation id, IBlockBuilder builder) {
        MenuType<CustomContainerMenu> menu = CustomContainerRegistry.CONTAINER_MENUS.get(id);
        if (menu == null) {
            return;
        }
        putOrRemove(CustomContainerRegistry.CONTAINER_TEXTURES, menu, builder.getContainerTexture());
        putOrRemove(CustomContainerRegistry.CONTAINER_SLOT_TEXTURES, menu, builder.getSlotTexture());
        putOrRemove(CustomContainerRegistry.CONTAINER_BARS, menu,
                builder.getGuiBars().isEmpty() ? null : builder.getGuiBars());
        putOrRemove(CustomContainerRegistry.CONTAINER_BUTTONS, menu,
                builder.getButtons().isEmpty() ? null : builder.getButtons());
        putOrRemove(CustomContainerRegistry.CONTAINER_LABELS, menu,
                builder.getLabels().isEmpty() ? null : builder.getLabels());
    }

    private static <K, V> void putOrRemove(Map<K, V> map, K key, V value) {
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }
}

