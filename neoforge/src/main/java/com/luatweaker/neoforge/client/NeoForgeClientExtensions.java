package com.luatweaker.neoforge.client;

import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.integration.geckolib.LuaDynamicGeoModel;
import com.luatweaker.common.integration.geckolib.LuaGeoItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Registers NeoForge client extensions (such as GeckoLib custom item renderers).
 */
public final class NeoForgeClientExtensions {

    private NeoForgeClientExtensions() {
    }

    public static void register(@NotNull IEventBus modBus) {
        modBus.addListener(NeoForgeClientExtensions::onRegisterClientExtensions);
    }

    private static void onRegisterClientExtensions(@NotNull RegisterClientExtensionsEvent event) {
        for (Item item : LuaTweakerCommon.getCreatedItems().values()) {
            if (item instanceof LuaGeoItem geoItem) {
                registerGeoItemExtension(event, geoItem);
            }
        }
    }

    private static void registerGeoItemExtension(RegisterClientExtensionsEvent event, LuaGeoItem geoItem) {
        event.registerItem(new GeoItemExtension(geoItem), geoItem);
    }

    private static final class GeoItemExtension implements IClientItemExtensions {
        private final LuaGeoItem item;
        private BlockEntityWithoutLevelRenderer renderer;

        GeoItemExtension(LuaGeoItem item) {
            this.item = item;
        }

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
                this.renderer = new GeoItemRenderer<>(new LuaDynamicGeoModel<>(this.item.getGeoSpec(), "item"));
            }
            return this.renderer;
        }
    }
}
