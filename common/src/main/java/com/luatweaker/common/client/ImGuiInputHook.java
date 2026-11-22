package com.luatweaker.common.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.client.gui.imgui.ImGuiManager;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards raw GLFW input to Dear ImGui and stops the game from also
 * consuming it while an ImGui window owns that device.
 */
public final class ImGuiInputHook {
    private static final long NO_WINDOW_HANDLE = 0L;
    private static final String FORWARD_FAILED_PREFIX = "[ImGui] ";

    private ImGuiInputHook() {
    }

    public static void init() {
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register((client, button, action, mods) -> onMouseButtonPre(button, action, mods));
        ClientRawInputEvent.MOUSE_SCROLLED.register((client, scrollX, scrollY) -> onMouseScroll(scrollX, scrollY));
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> onKey(keyCode, scanCode, action, modifiers));
        ClientScreenInputEvent.CHAR_TYPED_PRE.register((client, screen, character, modifiers) -> onCharTyped(character));
    }

    private static MinecraftClientPlatform platformOrNull() {
        com.luatweaker.api.pal.IPlatformClient client =
                com.luatweaker.api.pal.Platform.getClientOrNull();
        return client instanceof MinecraftClientPlatform platform ? platform : null;
    }

    private static long windowHandle() {
        if (Minecraft.getInstance().getWindow() == null) {
            return NO_WINDOW_HANDLE;
        }
        return Minecraft.getInstance().getWindow().getWindow();
    }

    private static void forward(@NotNull String what, @NotNull Runnable call) {
        ImGuiNativeGuard.run(call, failure -> LuaTweakerLog.get().debug(LogStage.SYSTEM,
                FORWARD_FAILED_PREFIX + what + " forward failed: " + failure.getMessage()));
    }

    public static EventResult onMouseButtonPre(int button, int action, int modifiers) {
        MinecraftClientPlatform platform = platformOrNull();
        if (platform == null) {
            return EventResult.pass();
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("mouse", () -> ImGuiManager.onMouseButton(handle, button, action, modifiers));
        }
        if (platform.imguiWantsMouseForGame()) {
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    public static EventResult onMouseScroll(double scrollDeltaX, double scrollDeltaY) {
        MinecraftClientPlatform platform = platformOrNull();
        if (platform == null) {
            return EventResult.pass();
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("scroll", () -> ImGuiManager.onScroll(handle, scrollDeltaX, scrollDeltaY));
        }
        if (platform.imguiWantsMouseForGame()) {
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    public static EventResult onKey(int key, int scanCode, int action, int modifiers) {
        if (platformOrNull() == null) {
            return EventResult.pass();
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("key", () -> ImGuiManager.onKey(handle, key, scanCode, action, modifiers));
        }
        return EventResult.pass();
    }

    public static EventResult onCharTyped(char codePoint) {
        MinecraftClientPlatform platform = platformOrNull();
        if (platform == null) {
            return EventResult.pass();
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("char", () -> ImGuiManager.onChar(handle, codePoint));
        }
        if (platform.imguiWantsTextInput()) {
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }
}
