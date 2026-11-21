package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.*;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class TabRegistrar {
    private static final String DEFAULT_TAB_ID = "custom_items";
    private static final String DEFAULT_ICON_ITEM = "luatweaker:custom_ruby";

    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Set<ResourceLocation> createdCustomTabs;
    private final Function<String, ResourceLocation> locationParser;
    private final TabContentFiller contentFiller;

    public TabRegistrar(IContentService contentService, Map<ResourceLocation, Item> createdItems,
                        Set<ResourceLocation> createdCustomTabs, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.createdCustomTabs = createdCustomTabs;
        this.locationParser = locationParser;
        this.contentFiller = new TabContentFiller(this, contentService, createdItems, createdCustomTabs);
    }

    ResourceLocation parseTabLocation(String tabId) {
        if (tabId == null || tabId.isBlank()) {
            return locationParser.apply(DEFAULT_TAB_ID);
        }
        if (tabId.contains(":")) {
            return ResourceLocation.parse(tabId);
        }
        ResourceLocation mcLoc =
                ResourceLocation.fromNamespaceAndPath("minecraft", tabId.toLowerCase(java.util.Locale.ROOT));
        if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(mcLoc)) {
            return mcLoc;
        }
        return locationParser.apply(tabId);
    }

    public void registerCreativeTab(RegisterEvent event) {
        createdCustomTabs.clear();
        var userTabs = contentService.getRegisteredTabs();
        if (userTabs.isEmpty()) {
            registerDefaultTab(event);
        } else {
            registerUserTabs(event, userTabs);
        }
    }

    private void registerUserTabs(RegisterEvent event, Collection<ICreativeTabBuilder> userTabs) {
        for (ICreativeTabBuilder tabBuilder : userTabs) {
            ResourceLocation tabLoc = locationParser.apply(tabBuilder.getId());
            if (!createdCustomTabs.add(tabLoc)) {
                continue;
            }
            event.register(Registries.CREATIVE_MODE_TAB, tabLoc, () -> buildTab(tabBuilder, tabLoc));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Creative Tab: " + tabLoc);
        }
    }

    private CreativeModeTab buildTab(ICreativeTabBuilder tabBuilder, ResourceLocation tabLoc) {
        return CreativeModeTab.builder()
                .title(Component.literal(tabBuilder.getTitle() != null ? tabBuilder.getTitle() : tabBuilder.getId()))
                .icon(() -> iconFor(tabBuilder))
                .displayItems((parameters, output) -> contentFiller.fillCustomTab(tabLoc, output))
                .build();
    }

    private ItemStack iconFor(ICreativeTabBuilder tabBuilder) {
        String iconId = tabBuilder.getIconItem() != null ? tabBuilder.getIconItem() : DEFAULT_ICON_ITEM;
        Item item = createdItems.get(locationParser.apply(iconId));
        if (item == null) {
            item = BuiltInRegistries.ITEM.get(locationParser.apply(iconId));
        }
        return new ItemStack(item != null && item != Items.AIR ? item : Items.DIAMOND);
    }

    private void registerDefaultTab(RegisterEvent event) {
        ResourceLocation defaultTabId = locationParser.apply(DEFAULT_TAB_ID);
        createdCustomTabs.add(defaultTabId);
        event.register(Registries.CREATIVE_MODE_TAB, defaultTabId, () -> CreativeModeTab.builder()
                .title(Component.literal("LuaTweaker Content"))
                .icon(() -> new ItemStack(createdItems.values().stream().findFirst().orElse(Items.DIAMOND)))
                .displayItems((parameters, output) -> {
                    for (Item item : createdItems.values()) {
                        output.accept(item);
                    }
                })
                .build());
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Default Creative Tab: " + defaultTabId);
    }

    public void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        contentFiller.onBuildCreativeModeTabContents(event);
    }
}
