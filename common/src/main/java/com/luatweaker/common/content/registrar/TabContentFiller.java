package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.content.IItemBuilder;
import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.Map;
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

    void populateVanillaTabs() {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            appendItemIfPresent(builder.getId(), builder.getCreativeTab());
        }
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            appendItemIfPresent(builder.getId(), builder.getCreativeTab());
        }
        populateFluidTabs();
        populateEntityTabs();
    }

    private void appendItemIfPresent(String id, String tabName) {
        Item item = createdItems.get(locationParser.apply(id));
        if (item != null) {
            appendTabIfCustom(tabName, item);
        }
    }

    private void populateFluidTabs() {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            Item bucket = createdItems.get(locationParser.apply(builder.getId() + "_bucket"));
            if (bucket != null) {
                appendTabIfCustom(builder.getCreativeTab(), bucket);
                CreativeTabRegistry.append(CreativeModeTabs.TOOLS_AND_UTILITIES, bucket);
            }
        }
    }

    private void populateEntityTabs() {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            if (!builder.hasSpawnEgg()) {
                continue;
            }
            Item egg = createdItems.get(locationParser.apply(builder.getId() + "_spawn_egg"));
            if (egg != null) {
                appendTabIfCustom(builder.getCreativeTab(), egg);
                CreativeTabRegistry.append(CreativeModeTabs.SPAWN_EGGS, egg);
            }
        }
    }

    private void appendTabIfCustom(String tabName, Item item) {
        if (tabName == null) {
            return;
        }
        ResourceLocation tab = locationParser.apply(tabName);
        if (!createdCustomTabs.contains(tab)) {
            CreativeTabRegistry.append(ResourceKey.create(Registries.CREATIVE_MODE_TAB, tab), item);
        }
    }

    void fillCustomTab(ResourceLocation tabLoc, CreativeModeTab.Output output) {
        addItems(tabLoc, output);
        addBlocks(tabLoc, output);
        addFluids(tabLoc, output);
        addEggs(tabLoc, output);
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
