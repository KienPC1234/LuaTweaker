package com.luatweaker.fabric.client;

import com.luatweaker.common.client.MinecraftWorldRenderEventListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class LuaTweakerFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                MinecraftWorldRenderEventListener.onRenderLevel(
                        context.matrixStack(),
                        context.camera(),
                        context.projectionMatrix(),
                        context.tickCounter().getGameTimeDeltaTicks()
                )
        );
    }
}
