package com.luatweaker.common.content.registrar;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

final class FluidClientVisuals {

    private FluidClientVisuals() {
    }

    @SuppressWarnings("unchecked")
    static void register(List<FluidRegistrar.RegisteredFluidEntry> entries) {
        for (FluidRegistrar.RegisteredFluidEntry entry : entries) {
            int tint = entry.builder().getColor() | 0xFF000000;
            ColorHandlerRegistry.registerItemColors(
                    (stack, tintIndex) -> tintIndex == 1 ? tint : -1, entry.bucket());
            ClientLifecycleEvent.CLIENT_SETUP.register(client ->
                    RenderTypeRegistry.register(
                            RenderType.translucent(), entry.still().get(), entry.flowing().get()));
        }
    }
}
