package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.*;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class TabRegistrar {
    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Set<ResourceLocation> createdCustomTabs;
    private final Function<String, ResourceLocation> locationParser;

    public TabRegistrar(IContentService contentService, Map<ResourceLocation, Item> createdItems, Set<ResourceLocation> createdCustomTabs, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.createdCustomTabs = createdCustomTabs;
        this.locationParser = locationParser;
    }

    public void registerCreativeTab(RegisterEvent event) {
        createdCustomTabs.clear();
        var userTabs = contentService.getRegisteredTabs();
        if (!userTabs.isEmpty()) {
            for (ICreativeTabBuilder tabBuilder : userTabs) {
                ResourceLocation tabLoc = locationParser.apply(tabBuilder.getId());
                if (!createdCustomTabs.add(tabLoc)) continue;

                event.register(Registries.CREATIVE_MODE_TAB, tabLoc, () -> CreativeModeTab.builder()
                        .title(Component.literal(tabBuilder.getTitle() != null ? tabBuilder.getTitle() : tabBuilder.getId()))
                        .icon(() -> {
                            String iconId = tabBuilder.getIconItem() != null ? tabBuilder.getIconItem() : "luatweaker:custom_ruby";
                            Item item = createdItems.get(locationParser.apply(iconId));
                            if (item == null) item = BuiltInRegistries.ITEM.get(locationParser.apply(iconId));
                            return new ItemStack(item != null && item != Items.AIR ? item : Items.DIAMOND);
                        })
                        .displayItems((parameters, output) -> {
                            for (IItemBuilder itemBuilder : contentService.getRegisteredItems()) {
                                String tId = itemBuilder.getCreativeTab();
                                if (tId != null && locationParser.apply(tId).equals(tabLoc)) {
                                    Item itemObj = createdItems.get(locationParser.apply(itemBuilder.getId()));
                                    if (itemObj != null) output.accept(itemObj);
                                }
                            }
                            for (IBlockBuilder blockBuilder : contentService.getRegisteredBlocks()) {
                                String tId = blockBuilder.getCreativeTab();
                                if (tId != null && locationParser.apply(tId).equals(tabLoc)) {
                                    Item blockItem = createdItems.get(locationParser.apply(blockBuilder.getId()));
                                    if (blockItem != null) output.accept(blockItem);
                                }
                            }
                            for (IFluidBuilder fluidBuilder : contentService.getRegisteredFluids()) {
                                String tId = fluidBuilder.getCreativeTab();
                                if (tId != null && locationParser.apply(tId).equals(tabLoc)) {
                                    ResourceLocation bucketRl = locationParser.apply(fluidBuilder.getId() + "_bucket");
                                    Item bucketItem = createdItems.get(bucketRl);
                                    if (bucketItem != null) output.accept(bucketItem);

                                    ResourceLocation altBucketRl = locationParser.apply(fluidBuilder.getId().replace("_fluid", "") + "_bucket");
                                    Item altBucketItem = createdItems.get(altBucketRl);
                                    if (altBucketItem != null && altBucketItem != bucketItem) output.accept(altBucketItem);
                                }
                            }
                            for (IEntityBuilder entityBuilder : contentService.getRegisteredEntities()) {
                                if (entityBuilder.hasSpawnEgg()) {
                                    String tId = entityBuilder.getCreativeTab();
                                    if (tId != null && locationParser.apply(tId).equals(tabLoc)) {
                                        ResourceLocation eggRl = locationParser.apply(entityBuilder.getId() + "_spawn_egg");
                                        Item eggItem = createdItems.get(eggRl);
                                        if (eggItem != null) output.accept(eggItem);
                                    }
                                }
                            }
                        })
                        .build());
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Creative Tab: " + tabLoc);
            }
        } else {
            ResourceLocation defaultTabId = locationParser.apply("custom_items");
            createdCustomTabs.add(defaultTabId);
            event.register(Registries.CREATIVE_MODE_TAB, defaultTabId, () -> CreativeModeTab.builder()
                    .title(Component.literal("LuaTweaker Content"))
                    .icon(() -> {
                        Item iconItem = createdItems.values().stream().findFirst().orElse(Items.DIAMOND);
                        return new ItemStack(iconItem);
                    })
                    .displayItems((parameters, output) -> {
                        for (Item item : createdItems.values()) {
                            output.accept(item);
                        }
                    })
                    .build());
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Default Creative Tab: " + defaultTabId);
        }
    }

    public void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        boolean isCustomTab = createdCustomTabs.contains(event.getTabKey().location());

        // 1. Custom Items into Vanilla Tabs
        if (!isCustomTab) {
            for (IItemBuilder item : contentService.getRegisteredItems()) {
                ResourceLocation rl = locationParser.apply(item.getId());
                Item itemObj = createdItems.get(rl);
                if (itemObj != null && item.getCreativeTab() != null) {
                    ResourceLocation tabRl = locationParser.apply(item.getCreativeTab());
                    if (event.getTabKey().location().equals(tabRl)) {
                        event.accept(itemObj);
                    }
                }
            }

            // 2. Custom Blocks into Vanilla Tabs
            for (IBlockBuilder block : contentService.getRegisteredBlocks()) {
                ResourceLocation rl = locationParser.apply(block.getId());
                Item itemObj = createdItems.get(rl);
                if (itemObj != null && block.getCreativeTab() != null) {
                    ResourceLocation tabRl = locationParser.apply(block.getCreativeTab());
                    if (event.getTabKey().location().equals(tabRl)) {
                        event.accept(itemObj);
                    }
                }
            }

            // 3. Custom Fluid Buckets into Vanilla Tabs
            for (IFluidBuilder fluid : contentService.getRegisteredFluids()) {
                if (fluid.getCreativeTab() != null) {
                    ResourceLocation tabRl = locationParser.apply(fluid.getCreativeTab());
                    if (event.getTabKey().location().equals(tabRl)) {
                    ResourceLocation bucketRl = locationParser.apply(fluid.getId() + "_bucket");
                    Item bucketObj = createdItems.get(bucketRl);
                    if (bucketObj != null) event.accept(bucketObj);

                    ResourceLocation altBucketRl = locationParser.apply(fluid.getId().replace("_fluid", "") + "_bucket");
                    Item altBucketObj = createdItems.get(altBucketRl);
                    if (altBucketObj != null && altBucketObj != bucketObj) event.accept(altBucketObj);
                    }
                }
            }
        }
        // 4. Custom Entity Spawn Eggs (Add to custom tab if not custom tab AND vanilla SPAWN_EGGS tab!)
        for (IEntityBuilder entity : contentService.getRegisteredEntities()) {
            if (entity.hasSpawnEgg()) {
                ResourceLocation eggRl = locationParser.apply(entity.getId() + "_spawn_egg");
                Item eggItem = createdItems.get(eggRl);
                if (eggItem != null) {
                    if (!isCustomTab && entity.getCreativeTab() != null) {
                        ResourceLocation tabRl = locationParser.apply(entity.getCreativeTab());
                        if (event.getTabKey().location().equals(tabRl)) {
                            event.accept(eggItem);
                        }
                    }
                    if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
                        event.accept(eggItem);
                    }
                }
            }
        }
    }
}
