package com.luatweaker.neoforge.event;

import com.luatweaker.client.ShaderServiceImpl;
import com.luatweaker.common.client.MinecraftWorldRenderEventListener;
import com.luatweaker.common.content.registrar.ItemHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * NeoForge-specific gameplay, render-stage, and viewport listeners.
 */
public final class NeoForgeGameEvents {

    private NeoForgeGameEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(NeoForgeGameEvents.class);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ItemHooks.handleSwing(event.getEntity(), event.getItemStack());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            MinecraftWorldRenderEventListener.onRenderLevel(
                    event.getPoseStack(),
                    event.getCamera(),
                    event.getProjectionMatrix(),
                    event.getPartialTick().getGameTimeDeltaTicks()
            );
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
        if (delta != null) {
            event.setYaw(event.getYaw() + delta[0]);
            event.setPitch(event.getPitch() + delta[1]);
            event.setRoll(event.getRoll() + delta[2]);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
        if (delta != null && delta[3] != 0.0f) {
            event.setFOV(event.getFOV() + delta[3]);
        }
    }
}
