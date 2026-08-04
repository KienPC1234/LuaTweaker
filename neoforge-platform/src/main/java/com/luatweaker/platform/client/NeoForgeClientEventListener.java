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
            fireRunServiceSignal(engine, "RenderStepped", (double) net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaTicks());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine != null) {
            // Standard Minecraft tick is 50ms (0.05s)
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
    }
}
