package com.luatweaker.platform.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.platform.LuaTweakerMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public class NeoForgeClientEventListener {

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
                    ILuaValue fireFn = signal.rawget("Fire");
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
