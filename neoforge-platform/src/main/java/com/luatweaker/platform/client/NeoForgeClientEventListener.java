package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.client.CameraServiceImpl;
import com.luatweaker.client.ClientEffectsServiceImpl;
import com.luatweaker.client.ClientServiceImpl;
import com.luatweaker.client.TweenServiceImpl;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public final class NeoForgeClientEventListener {
    private static final String SIGNAL_FIRE_SYNC = "FireSync";
    /** Minecraft tick length in seconds: {@code getGameTimeDeltaTicks()} counts 50 ms units. */
    private static final double SECONDS_PER_TICK = 0.05;

    private NeoForgeClientEventListener() {
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }
        double deltaTime = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();
        com.luatweaker.platform.runservice.RunServiceDispatcher.fire(engine, "RenderStepped", deltaTime);
        // Tweens advance on real time; the raw value is in ticks (1 tick = 50 ms).
        TweenServiceImpl.tickAll(deltaTime * SECONDS_PER_TICK);
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
        boolean hasLocalServer = Platform.isInitialized() && Platform.getContent().hasLocalServer();
        if (!com.luatweaker.tasks.TaskPumpPolicy.pumpFromClientTick(hasLocalServer)) {
            return;
        }
        // No server loop in this process: pump Task.spawn/delay/wait and async Signal:Fire
        // here, otherwise client-side scripts would never advance.
        LuaTweakerMod.tickActiveEngineTasks();
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
        forwardKeySignal(event, pressed, released);
    }

    @SubscribeEvent
    public static void onMouseButton(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Post event) {
        boolean pressed = event.getAction() == GLFW_PRESS;
        boolean released = event.getAction() == GLFW_RELEASE;
        if (pressed || released) {
            updateMouseState(event.getButton(), pressed);
        }
        forwardMouseSignal(event, pressed, released);
    }

    private static void forwardKeySignal(@NotNull net.neoforged.neoforge.client.event.InputEvent.Key event,
                                         boolean pressed, boolean released) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null || !(pressed || released)) {
            return;
        }
        boolean gameProcessed = net.minecraft.client.Minecraft.getInstance().screen != null;
        String signalName = pressed ? "InputBegan" : "InputEnded";
        UserInputDispatcher.fire(engine, signalName, UserInputDispatcher.TYPE_KEY,
                event.getKey(), event.getAction(), event.getModifiers(), gameProcessed);
    }

    private static void forwardMouseSignal(
            @NotNull net.neoforged.neoforge.client.event.InputEvent.MouseButton.Post event,
            boolean pressed, boolean released) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null || !(pressed || released)) {
            return;
        }
        boolean gameProcessed = net.minecraft.client.Minecraft.getInstance().screen != null;
        String signalName = pressed ? "InputBegan" : "InputEnded";
        UserInputDispatcher.fire(engine, signalName, UserInputDispatcher.TYPE_MOUSE,
                event.getButton(), event.getAction(), event.getModifiers(), gameProcessed);
    }

    private static void updateKeyState(int keyCode, boolean pressed) {
        Object service = LuaServiceRegistry.get("ClientService");
        if (service instanceof ClientServiceImpl clientService) {
            clientService.onKeyPress(keyCode, pressed);
        }
    }

    private static void updateMouseState(int button, boolean pressed) {
        Object service = LuaServiceRegistry.get("ClientService");
        if (service instanceof ClientServiceImpl clientService) {
            clientService.onMouseButton(button, pressed);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(@NotNull RenderGuiEvent.Post event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }
        NeoForgeGuiService.setGraphics(event.getGuiGraphics());
        try {
            RenderFirewall.run(() -> fireHudSignal(engine, event),
                    () -> "GuiService.OnRenderHUD dispatch failed");
        } finally {
            NeoForgeGuiService.clearGraphics();
        }
        drawScreenFlash(event.getGuiGraphics());
        runImGuiFrame(engine, event.getGuiGraphics());
    }

    /** Screen flash overlay draws above everything, so it is applied after the Lua HUD. */
    private static void drawScreenFlash(@NotNull GuiGraphics graphics) {
        int flashColor = ClientEffectsServiceImpl.getActiveFlashColor();
        if (flashColor == 0) {
            return;
        }
        int width = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, width, height, flashColor);
    }

    /**
     * Fires {@code GuiService.OnRenderHUD} synchronously: {@code FireSync} invokes the HUD
     * listeners on the render thread while the GuiGraphics context is still active. The async
     * {@code Signal:Fire} would defer drawing to the next server tick, where no graphics context
     * exists and every draw call no-ops.
     */
    private static void fireHudSignal(@NotNull ILuaEngine engine, @NotNull RenderGuiEvent.Post event) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue guiServiceValue = globals.rawget("GuiService");
        if (guiServiceValue == null || !guiServiceValue.isTable()) {
            return;
        }
        ILuaValue onRender = guiServiceValue.asTable().rawget("OnRenderHUD");
        if (onRender == null || !onRender.isTable()) {
            return;
        }
        ILuaTable signal = onRender.asTable();
        ILuaValue fireFn = resolveFireSync(globals, signal);
        if (fireFn != null) {
            engine.callFunction(fireFn, signal,
                    engine.wrapNumber(event.getPartialTick().getGameTimeDeltaTicks()));
        }
    }

    /**
     * Engine-owned Dear ImGui frame: open, fire {@code ImGui.OnFrame}
     * synchronously (same render thread, same contract as
     * {@code GuiService.OnRenderHUD}), draw deferred item icons, close.
     * Failures are logged, never thrown — the render loop must survive Lua.
     */
    private static void runImGuiFrame(@NotNull ILuaEngine engine, @NotNull GuiGraphics graphics) {
        IPlatformClient client = Platform.getClientOrNull();
        if (!(client instanceof NeoForgeClientPlatform platform)) {
            return;
        }
        ImGuiNativeGuard.run(() -> drawImGuiFrame(platform, engine, graphics),
                failure -> LuaTweakerLog.get().debug(LogStage.SYSTEM,
                        "ImGui.OnFrame dispatch failed: " + failure.getMessage()));
    }

    private static void drawImGuiFrame(@NotNull NeoForgeClientPlatform platform, @NotNull ILuaEngine engine,
                                       @NotNull GuiGraphics graphics) {
        platform.imguiBeginFrame();
        if (!platform.imguiIsFrameOpen()) {
            return;
        }
        try {
            RenderFirewall.run(() -> fireImGuiSignal(engine), () -> "ImGui.OnFrame dispatch failed");
        } finally {
            platform.imguiEndFrame();
            // Item icons recorded during the frame draw on top of ImGui.
            platform.flushImGuiItems(graphics);
        }
    }

    private static void fireImGuiSignal(@NotNull ILuaEngine engine) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue imguiValue = globals.rawget("ImGui");
        if (imguiValue == null || !imguiValue.isTable()) {
            return;
        }
        ILuaValue onFrame = imguiValue.asTable().rawget("OnFrame");
        if (onFrame == null || !onFrame.isTable()) {
            return;
        }
        ILuaTable signal = onFrame.asTable();
        ILuaValue fireFn = resolveFireSync(globals, signal);
        if (fireFn != null) {
            engine.callFunction(fireFn, signal);
        }
    }

    /** Resolves {@code Signal.FireSync}, preferring the signal's own field over the class table. */
    private static @Nullable ILuaValue resolveFireSync(@NotNull ILuaTable globals, @NotNull ILuaTable signal) {
        ILuaValue fireFn = signal.rawget(SIGNAL_FIRE_SYNC);
        if (fireFn != null && fireFn.isFunction()) {
            return fireFn;
        }
        ILuaValue signalClass = globals.rawget("Signal");
        if (signalClass == null || !signalClass.isTable()) {
            return null;
        }
        ILuaValue classFire = signalClass.asTable().rawget(SIGNAL_FIRE_SYNC);
        return classFire != null && classFire.isFunction() ? classFire : null;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        float[] shake = CameraServiceImpl.getCameraShakeOffsets();
        if (shake != null && shake.length == 3) {
            event.setYaw(event.getYaw() + shake[0]);
            event.setPitch(event.getPitch() + shake[1]);
            event.setRoll(event.getRoll() + shake[2]);
        }
    }
}
