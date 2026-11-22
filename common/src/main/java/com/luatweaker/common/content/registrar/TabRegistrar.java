package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.ICreativeTabBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    public void registerCreativeTabs() {
        Registrar<CreativeModeTab> registrar =
                RegistrarManager.get("luatweaker").get(Registries.CREATIVE_MODE_TAB);
        createdCustomTabs.clear();
        var userTabs = contentService.getRegisteredTabs();
        if (userTabs.isEmpty()) {
            registerDefaultTab(registrar);
        } else {
            registerUserTabs(registrar, userTabs);
        }
        contentFiller.populateVanillaTabs();
    }

    private void registerUserTabs(Registrar<CreativeModeTab> registrar, Collection<ICreativeTabBuilder> userTabs) {
        for (ICreativeTabBuilder tabBuilder : userTabs) {
            ResourceLocation tabLoc = locationParser.apply(tabBuilder.getId());
            if (!createdCustomTabs.add(tabLoc)) {
                continue;
            }
            registrar.register(tabLoc, () -> buildTab(tabBuilder, tabLoc));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Creative Tab: " + tabLoc);
        }
    }

    private CreativeModeTab buildTab(ICreativeTabBuilder tabBuilder, ResourceLocation tabLoc) {
        return dev.architectury.registry.CreativeTabRegistry.create(builder -> builder
                .title(Component.literal(tabBuilder.getTitle() != null ? tabBuilder.getTitle() : tabBuilder.getId()))
                .icon(() -> iconFor(tabBuilder))
                .displayItems((parameters, output) -> contentFiller.fillCustomTab(tabLoc, output)));
    }

    private ItemStack iconFor(ICreativeTabBuilder tabBuilder) {
        String iconId = tabBuilder.getIconItem() != null ? tabBuilder.getIconItem() : DEFAULT_ICON_ITEM;
        Item item = createdItems.get(locationParser.apply(iconId));
        if (item == null) {
            item = BuiltInRegistries.ITEM.get(locationParser.apply(iconId));
        }
        return new ItemStack(item != null && item != Items.AIR ? item : Items.DIAMOND);
    }

    private void registerDefaultTab(Registrar<CreativeModeTab> registrar) {
        ResourceLocation defaultTabId = locationParser.apply(DEFAULT_TAB_ID);
        createdCustomTabs.add(defaultTabId);
        registrar.register(defaultTabId, () -> dev.architectury.registry.CreativeTabRegistry.create(builder -> builder
                .title(Component.literal("LuaTweaker Content"))
                .icon(() -> new ItemStack(createdItems.values().stream().findFirst().orElse(Items.DIAMOND)))
                .displayItems((parameters, output) -> {
                    for (Item item : createdItems.values()) {
                        output.accept(item);
                    }
                })));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Default Creative Tab: " + defaultTabId);
    }
}
