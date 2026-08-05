package com.luatweaker.platform.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.platform.LuaTweakerMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public class NeoForgeClientEventListener {

    private static void fireRunServiceSignal(ILuaEngine engine, String signalName, double deltaTime) {
        try {
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaValue runServiceVal = globals.rawget("RunService");
            if (runServiceVal != null && runServiceVal.isTable()) {
                ILuaTable runService = runServiceVal.asTable();
                ILuaValue signalObj = runService.rawget(signalName);
                if (signalObj != null && signalObj.isTable()) {
                    ILuaTable signal = signalObj.asTable();
                    ILuaValue fireFn = signal.rawget("FireSync");
                    if (fireFn == null || !fireFn.isFunction()) {
                        ILuaValue signalClass = globals.rawget("Signal");
                        if (signalClass != null && signalClass.isTable()) {
                            fireFn = signalClass.asTable().rawget("FireSync");
                        }
                    }
                    if (fireFn != null && fireFn.isFunction()) {
                        engine.callFunction(fireFn, signal, engine.wrapNumber(deltaTime));
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine != null) {
            double deltaTime = (double) net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();
            fireRunServiceSignal(engine, "RenderStepped", deltaTime);
            // Tween updates should use real time delta in seconds. Minecraft ticks are 1/20th of a second.
            // But gameTimeDeltaTicks is partial tick fraction? No, getTimer().getGameTimeDeltaTicks() is in ticks (1 tick = 50ms = 0.05s).
            // Actually, for real-time tweening, we should probably use real delta time, but since it's game ticks we just multiply by 0.05.
            com.luatweaker.client.TweenServiceImpl.tickAll(deltaTime * 0.05);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine != null) {
            // Standard Minecraft tick is 50ms (0.05s).
            // NOTE: the Lua task queues are pumped from the SERVER tick only
            // (LuaTweakerMod.onServerTick); pumping here too would resume
            // coroutines from a second thread and freeze the game.
            fireRunServiceSignal(engine, "Heartbeat", 0.05);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }

        NeoForgeGuiService.setGraphics(event.getGuiGraphics());
        try {
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaValue guiServiceVal = globals.rawget("GuiService");
            if (guiServiceVal != null && guiServiceVal.isTable()) {
                ILuaTable guiService = guiServiceVal.asTable();
                ILuaValue onRender = guiService.rawget("OnRenderHUD");
                if (onRender != null && onRender.isTable()) {
                    ILuaTable signal = onRender.asTable();
                    // FireSync invokes the HUD listeners synchronously on the render
                    // thread, while the GuiGraphics context is still active. Using the
                    // async Signal:Fire would defer drawing to the next server tick,
                    // where no graphics context exists and all draw calls no-op.
                    ILuaValue fireFn = signal.rawget("FireSync");
                    if (fireFn == null || !fireFn.isFunction()) {
                        ILuaValue signalClass = globals.rawget("Signal");
                        if (signalClass != null && signalClass.isTable()) {
                            fireFn = signalClass.asTable().rawget("FireSync");
                        }
                    }
                    if (fireFn != null && fireFn.isFunction()) {
                        engine.callFunction(fireFn, signal, engine.wrapNumber(event.getPartialTick().getGameTimeDeltaTicks()));
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore frame render errors to prevent crashing client loop
        } finally {
            NeoForgeGuiService.clearGraphics();
        }

        // Render screen flash overlay on top of everything
        int flashColor = com.luatweaker.client.ClientEffectsServiceImpl.getActiveFlashColor();
        if (flashColor != 0) {
            int width = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int height = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, width, height, flashColor);
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        float[] shake = com.luatweaker.client.CameraServiceImpl.getCameraShakeOffsets();
        if (shake != null && shake.length == 3) {
            event.setYaw(event.getYaw() + shake[0]);
            event.setPitch(event.getPitch() + shake[1]);
            event.setRoll(event.getRoll() + shake[2]);
        }
    }
}
