package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.content.IItemBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Places registered Lua content into custom and vanilla creative tabs. */
final class TabContentFiller {

    private final TabRegistrar registrar;
    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Set<ResourceLocation> createdCustomTabs;
    private final Function<String, ResourceLocation> locationParser;

    TabContentFiller(TabRegistrar registrar, IContentService contentService,
                     Map<ResourceLocation, Item> createdItems, Set<ResourceLocation> createdCustomTabs) {
        this.registrar = registrar;
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.createdCustomTabs = createdCustomTabs;
        this.locationParser = registrar::parseTabLocation;
    }

    void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        boolean isCustomTab = createdCustomTabs.contains(tab);
        if (!isCustomTab) {
            addVanillaItems(event, tab);
            addVanillaBlocks(event, tab);
            addVanillaFluids(event, tab);
        }
        addSpawnEggs(event, tab, isCustomTab);
    }

    void fillCustomTab(ResourceLocation tabLoc, CreativeModeTab.Output output) {
        addItems(tabLoc, output);
        addBlocks(tabLoc, output);
        addFluids(tabLoc, output);
        addEggs(tabLoc, output);
    }

    private void addVanillaItems(BuildCreativeModeTabContentsEvent event, ResourceLocation tab) {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            Item item = createdItems.get(locationParser.apply(builder.getId()));
            if (item != null && builder.getCreativeTab() != null
                    && tab.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                event.accept(item);
            }
        }
    }

    private void addVanillaBlocks(BuildCreativeModeTabContentsEvent event, ResourceLocation tab) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            Item item = createdItems.get(locationParser.apply(builder.getId()));
            if (item != null && builder.getCreativeTab() != null
                    && tab.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                event.accept(item);
            }
        }
    }

    private void addVanillaFluids(BuildCreativeModeTabContentsEvent event, ResourceLocation tab) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            Item bucket = createdItems.get(locationParser.apply(builder.getId() + "_bucket"));
            if (bucket != null && builder.getCreativeTab() != null
                    && tab.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                event.accept(bucket);
            }
            if (bucket != null && event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
                event.accept(bucket);
            }
        }
    }

    private void addSpawnEggs(BuildCreativeModeTabContentsEvent event, ResourceLocation tab, boolean isCustomTab) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            if (!builder.hasSpawnEgg()) {
                continue;
            }
            Item egg = createdItems.get(locationParser.apply(builder.getId() + "_spawn_egg"));
            if (egg == null) {
                continue;
            }
            if (!isCustomTab && builder.getCreativeTab() != null
                    && tab.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                event.accept(egg);
            }
            if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
                event.accept(egg);
            }
        }
    }

    private void addItems(ResourceLocation tabLoc, CreativeModeTab.Output output) {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            if (builder.getCreativeTab() == null || !tabLoc.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                continue;
            }
            Item item = createdItems.get(locationParser.apply(builder.getId()));
            if (item != null) {
                output.accept(item);
            }
        }
    }

    private void addBlocks(ResourceLocation tabLoc, CreativeModeTab.Output output) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if (builder.getCreativeTab() == null || !tabLoc.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                continue;
            }
            Item item = createdItems.get(locationParser.apply(builder.getId()));
            if (item != null) {
                output.accept(item);
            }
        }
    }

    private void addFluids(ResourceLocation tabLoc, CreativeModeTab.Output output) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            if (builder.getCreativeTab() == null || !tabLoc.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                continue;
            }
            ResourceLocation bucketId = locationParser.apply(builder.getId() + "_bucket");
            Item bucket = createdItems.get(bucketId);
            if (bucket != null) {
                output.accept(bucket);
            }
            ResourceLocation altBucketId =
                    locationParser.apply(builder.getId().replace("_fluid", "") + "_bucket");
            Item altBucket = createdItems.get(altBucketId);
            if (altBucket != null && !Objects.equals(altBucket, bucket)) {
                output.accept(altBucket);
            }
        }
    }

    private void addEggs(ResourceLocation tabLoc, CreativeModeTab.Output output) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            if (!builder.hasSpawnEgg() || builder.getCreativeTab() == null
                    || !tabLoc.equals(registrar.parseTabLocation(builder.getCreativeTab()))) {
                continue;
            }
            Item egg = createdItems.get(locationParser.apply(builder.getId() + "_spawn_egg"));
            if (egg != null) {
                output.accept(egg);
            }
        }
    }
}



