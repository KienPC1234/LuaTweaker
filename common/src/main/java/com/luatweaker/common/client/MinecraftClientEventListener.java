package com.luatweaker.common.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.client.ClientEffectsServiceImpl;
import com.luatweaker.client.ClientServiceImpl;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.core.signal.LuaSignalDispatcher;
import com.luatweaker.core.service.LuaServiceRegistry;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public final class MinecraftClientEventListener {
    private MinecraftClientEventListener() {
    }

    public static void init() {
        ClientTickEvent.CLIENT_POST.register(client -> onClientTick());
        ClientGuiEvent.RENDER_HUD.register(MinecraftClientEventListener::onRenderHud);
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            onInputKey(keyCode, action, modifiers);
            return dev.architectury.event.EventResult.pass();
        });
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register((client, button, action, mods) -> {
            onMouseButton(button, action, mods);
            return dev.architectury.event.EventResult.pass();
        });
        InteractionEvent.CLIENT_LEFT_CLICK_AIR.register((player, hand) -> {
            if (hand == InteractionHand.MAIN_HAND && player != null) {
                net.minecraft.world.item.ItemStack held = player.getMainHandItem();
                if (!held.isEmpty()
                        && com.luatweaker.common.content.registrar.ItemRegistrar.BUILDER_BY_ITEM.containsKey(held.getItem())
                        && Platform.isInitialized()) {
                    Platform.getNetwork().sendPayloadPacketToServer(
                            com.luatweaker.common.network.CommonNetworkHandler.ITEM_SWING_CHANNEL, "");
                }
            }
        });
        ClientExtraEventForwarder.init();
    }

    public static void onClientTick() {
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
        if (engine == null) {
            return;
        }
        boolean hasLocalServer = Platform.isInitialized() && Platform.getContent().hasLocalServer();
        if (!com.luatweaker.tasks.TaskPumpPolicy.pumpFromClientTick(hasLocalServer)) {
            return;
        }
        LuaTweakerCommon.tickActiveEngineTasks();
        com.luatweaker.common.runservice.RunServiceDispatcher.fire(
                engine, "Stepped", com.luatweaker.common.runservice.ServerTickClock.NOMINAL_DELTA_SECONDS);
        com.luatweaker.common.runservice.RunServiceDispatcher.fire(
                engine, "Heartbeat", com.luatweaker.common.runservice.ServerTickClock.NOMINAL_DELTA_SECONDS);
    }

    public static void onInputKey(int keyCode, int action, int modifiers) {
        boolean pressed = action == GLFW_PRESS;
        boolean released = action == GLFW_RELEASE;
        if (pressed || released) {
            updateKeyState(keyCode, pressed);
        }
        forwardKeySignal(keyCode, action, modifiers, pressed, released);
    }

    public static void onMouseButton(int button, int action, int modifiers) {
        boolean pressed = action == GLFW_PRESS;
        boolean released = action == GLFW_RELEASE;
        if (pressed || released) {
            updateMouseState(button, pressed);
        }
        forwardMouseSignal(button, action, modifiers, pressed, released);
    }

    private static void forwardKeySignal(int keyCode, int action, int modifiers,
                                         boolean pressed, boolean released) {
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
        if (engine == null || !(pressed || released)) {
            return;
        }
        boolean gameProcessed = Minecraft.getInstance().screen != null;
        String signalName = pressed ? "InputBegan" : "InputEnded";
        UserInputDispatcher.fire(engine, signalName, UserInputDispatcher.TYPE_KEY,
                keyCode, action, modifiers, gameProcessed);
    }

    private static void forwardMouseSignal(int button, int action, int modifiers,
                                           boolean pressed, boolean released) {
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
        if (engine == null || !(pressed || released)) {
            return;
        }
        boolean gameProcessed = Minecraft.getInstance().screen != null;
        String signalName = pressed ? "InputBegan" : "InputEnded";
        UserInputDispatcher.fire(engine, signalName, UserInputDispatcher.TYPE_MOUSE,
                button, action, modifiers, gameProcessed);
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

    public static void onRenderHud(@NotNull GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        float deltaTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
        if (engine == null) {
            return;
        }
        IPlatformClient client = Platform.getClientOrNull();
        if (client instanceof MinecraftClientPlatform platform) {
            platform.updateShaderGlobals();
        }
        MinecraftGuiService.setGraphics(guiGraphics);
        try {
            RenderFirewall.run(() -> fireHudSignal(engine, deltaTicks),
                    () -> "GuiService.OnRenderHUD dispatch failed");
        } finally {
            MinecraftGuiService.clearGraphics();
        }
        drawScreenFlash(guiGraphics);
        if (!(Minecraft.getInstance().screen instanceof com.luatweaker.common.client.gui.imgui.ImGuiOverlayScreen)) {
            runImGuiFrame(engine, guiGraphics);
        }
    }

    private static void drawScreenFlash(@NotNull GuiGraphics graphics) {
        int flashColor = ClientEffectsServiceImpl.getActiveFlashColor();
        if (flashColor == 0) {
            return;
        }
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, width, height, flashColor);
    }

    private static void fireHudSignal(@NotNull ILuaEngine engine, float deltaTicks) {
        LuaSignalDispatcher.fireNumber(engine, "GuiService", "OnRenderHUD", deltaTicks);
    }

    public static void renderImGuiFrameFromScreen(@NotNull ILuaEngine engine, @NotNull GuiGraphics graphics) {
        runImGuiFrame(engine, graphics);
    }

    private static void runImGuiFrame(@NotNull ILuaEngine engine, @NotNull GuiGraphics graphics) {
        IPlatformClient client = Platform.getClientOrNull();
        if (!(client instanceof MinecraftClientPlatform platform)) {
            return;
        }
        ImGuiNativeGuard.run(() -> drawImGuiFrame(platform, engine, graphics),
                failure -> LuaTweakerLog.get().debug(LogStage.SYSTEM,
                        "ImGui.OnFrame dispatch failed: " + failure.getMessage()));
    }

    private static void drawImGuiFrame(@NotNull MinecraftClientPlatform platform, @NotNull ILuaEngine engine,
                                       @NotNull GuiGraphics graphics) {
        platform.imguiBeginFrame();
        if (!platform.imguiIsFrameOpen()) {
            return;
        }
        try {
            RenderFirewall.run(() -> fireImGuiSignal(engine), () -> "ImGui.OnFrame dispatch failed");
        } finally {
            platform.imguiEndFrame();
            platform.flushImGuiItems(graphics);
        }
    }

    private static void fireImGuiSignal(@NotNull ILuaEngine engine) {
        LuaSignalDispatcher.fire(engine, "ImGui", "OnFrame");
    }
}
