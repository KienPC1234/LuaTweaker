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

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public class NeoForgeClientEventListener {

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine != null) {
            double deltaTime = (double) net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();
            com.luatweaker.platform.runservice.RunServiceDispatcher.fire(engine, "RenderStepped", deltaTime);
            // Tween updates should use real time delta in seconds. Minecraft ticks are 1/20th of a second.
            // But gameTimeDeltaTicks is partial tick fraction? No, getTimer().getGameTimeDeltaTicks() is in ticks (1 tick = 50ms = 0.05s).
            // Actually, for real-time tweening, we should probably use real delta time, but since it's game ticks we just multiply by 0.05.
            com.luatweaker.client.TweenServiceImpl.tickAll(deltaTime * 0.05);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }
        // Single-player shares ONE engine between the integrated server and the client, and
        // LuaTweakerMod.onServerTick already pumps the task queues plus Stepped/Heartbeat for
        // it. Pumping here too would run every task twice per tick and resume coroutines from
        // a second thread. A remote client has no server tick at all, so its client tick is
        // the only pump that exists.
        boolean hasLocalServer = com.luatweaker.api.pal.Platform.isInitialized()
                && com.luatweaker.api.pal.Platform.getContent().hasLocalServer();
        if (!com.luatweaker.tasks.TaskPumpPolicy.pumpFromClientTick(hasLocalServer)) {
            return;
        }
        // No server loop in this process: pump Task.spawn/delay/wait and async Signal:Fire
        // here, otherwise client-side scripts would never advance.
        LuaTweakerMod.tickActiveEngineTasks();
        // Standard Minecraft tick is 50ms (0.05s).
        com.luatweaker.platform.runservice.RunServiceDispatcher.fire(
                engine, "Stepped", com.luatweaker.platform.runservice.ServerTickClock.NOMINAL_DELTA_SECONDS);
        com.luatweaker.platform.runservice.RunServiceDispatcher.fire(
                engine, "Heartbeat", com.luatweaker.platform.runservice.ServerTickClock.NOMINAL_DELTA_SECONDS);
    }

    /**
     * Raw keyboard input. Payload: (inputType, code, action, modifiers, gameProcessed) with
     * GLFW action codes 1=press, 0=release, 2=repeat. Only press/release are forwarded;
     * repeats would fire InputBegan several times per held key.
     */
    @SubscribeEvent
    public static void onInputKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        boolean pressed = event.getAction() == GLFW_PRESS;
        boolean released = event.getAction() == GLFW_RELEASE;
        if (pressed || released) {
            updateKeyState(event.getKey(), pressed);
        }
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }
        boolean gameProcessed = net.minecraft.client.Minecraft.getInstance().screen != null;
        if (pressed) {
            UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY,
                    event.getKey(), event.getAction(), event.getModifiers(), gameProcessed);
        } else if (released) {
            UserInputDispatcher.fire(engine, "InputEnded", UserInputDispatcher.TYPE_KEY,
                    event.getKey(), event.getAction(), event.getModifiers(), gameProcessed);
        }
    }

    @SubscribeEvent
    public static void onMouseButton(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Post event) {
        boolean pressed = event.getAction() == GLFW_PRESS;
        boolean released = event.getAction() == GLFW_RELEASE;
        if (pressed || released) {
            updateMouseState(event.getButton(), pressed);
        }
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }
        boolean gameProcessed = net.minecraft.client.Minecraft.getInstance().screen != null;
        if (pressed) {
            UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_MOUSE,
                    event.getButton(), event.getAction(), event.getModifiers(), gameProcessed);
        } else if (released) {
            UserInputDispatcher.fire(engine, "InputEnded", UserInputDispatcher.TYPE_MOUSE,
                    event.getButton(), event.getAction(), event.getModifiers(), gameProcessed);
        }
    }

    private static void updateKeyState(int keyCode, boolean pressed) {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("ClientService");
        if (service instanceof com.luatweaker.client.ClientServiceImpl clientService) {
            clientService.onKeyPress(keyCode, pressed);
        }
    }

    private static void updateMouseState(int button, boolean pressed) {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("ClientService");
        if (service instanceof com.luatweaker.client.ClientServiceImpl clientService) {
            clientService.onMouseButton(button, pressed);
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
            // Never crash the render loop, but leave a trace for HUD debugging.
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "GuiService.OnRenderHUD dispatch failed: " + t.getMessage());
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
