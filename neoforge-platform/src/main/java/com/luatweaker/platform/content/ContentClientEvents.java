package com.luatweaker.platform.content;

import com.luatweaker.platform.client.LuaMobRenderers;
import com.luatweaker.platform.container.CustomContainerMenu;
import com.luatweaker.platform.container.CustomContainerRegistry;
import com.luatweaker.platform.container.CustomContainerScreen;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-side registration hooks for Lua content (bow properties, renderers, screens, colors). */
public final class ContentClientEvents {
    private final NeoForgeContentRegistry parent;

    public ContentClientEvents(NeoForgeContentRegistry parent) {
        this.parent = parent;
    }

    @SubscribeEvent
    public void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BowItemProperties.register(parent));
    }

    @SubscribeEvent
    public void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        LuaMobRenderers.registerLayerDefinitions(event);
    }

    @SubscribeEvent
    public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ContentRendererRegistrar.register(parent, event);
    }

    @SubscribeEvent
    public void onRegisterScreens(RegisterMenuScreensEvent event) {
        for (MenuType<CustomContainerMenu> menuType : CustomContainerRegistry.CONTAINER_MENUS.values()) {
            event.register(menuType, CustomContainerScreen::new);
        }
    }

    @SubscribeEvent
    public void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        FluidBucketColors.register(parent, event);
    }
}
