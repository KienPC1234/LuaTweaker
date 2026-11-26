package com.luatweaker.neoforge.client;

import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.content.registrar.FluidRegistrar;
import com.luatweaker.common.content.registrar.MobEffectRegistrar;
import com.luatweaker.common.integration.geckolib.LuaDynamicGeoModel;
import com.luatweaker.common.integration.geckolib.LuaGeoItem;
import com.luatweaker.neoforge.client.effect.CustomMobEffectClientExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Registers NeoForge client extensions for GeckoLib items, Architectury custom fluids,
 * and custom status effect textures.
 */
public final class NeoForgeClientExtensions {

    private NeoForgeClientExtensions() {
    }

    public static void register(@NotNull IEventBus modBus) {
        modBus.addListener(NeoForgeClientExtensions::onRegisterClientExtensions);
    }

    private static void onRegisterClientExtensions(@NotNull RegisterClientExtensionsEvent event) {
        for (Item item : LuaTweakerCommon.getCreatedItems().values()) {
            if (item instanceof LuaGeoItem geoItem && !event.isItemRegistered(geoItem)) {
                registerGeoItemExtension(event, geoItem);
            }
        }
        FluidRegistrar.forEachRegisteredFluid(fluid -> registerFluidTypeExtension(event, fluid));
        MobEffectRegistrar.forEachCustomEffectTexture((effect, texture) -> {
            if (!event.isMobEffectRegistered(effect)) {
                event.registerMobEffect(new CustomMobEffectClientExtensions(texture), effect);
            }
        });
    }

    private static void registerFluidTypeExtension(@NotNull RegisterClientExtensionsEvent event,
                                                   @NotNull Fluid fluid) {
        FluidType fluidType = fluid.getFluidType();
        if (!event.isFluidTypeRegistered(fluidType)) {
            fluidType.initializeClient(extensions -> {
                if (!event.isFluidTypeRegistered(fluidType)) {
                    event.registerFluidType(extensions, fluidType);
                }
            });
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
