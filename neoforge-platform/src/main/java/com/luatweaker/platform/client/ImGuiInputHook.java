package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.platform.client.gui.imgui.ImGuiManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards raw GLFW input to Dear ImGui and stops the game from also
 * consuming it while an ImGui window owns that device.
 *
 * <p>Gating is deliberate and narrow: mouse clicks/scroll are swallowed only
 * when the last finished frame had a visible window that wanted the mouse.
 * Key events are forwarded so text fields work, but {@code InputEvent.Key}
 * is not cancelable by NeoForge design — the known limitation is documented
 * on the key handler. Normal gameplay (no window open) sees zero
 * interference.</p>
 */
@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public final class ImGuiInputHook {
    private static final long NO_WINDOW_HANDLE = 0L;
    private static final String FORWARD_FAILED_PREFIX = "[ImGui] ";

    private ImGuiInputHook() {
    }

    private static NeoForgeClientPlatform platformOrNull() {
        com.luatweaker.api.pal.IPlatformClient client =
                com.luatweaker.api.pal.Platform.getClientOrNull();
        return client instanceof NeoForgeClientPlatform platform ? platform : null;
    }

    private static long windowHandle() {
        if (net.minecraft.client.Minecraft.getInstance().getWindow() == null) {
            return NO_WINDOW_HANDLE;
        }
        return net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
    }

    /** Forwards one input call to ImGui; a native failure is logged, never fatal to the hook. */
    private static void forward(@NotNull String what, @NotNull Runnable call) {
        ImGuiNativeGuard.run(call, failure -> LuaTweakerLog.get().debug(LogStage.SYSTEM,
                FORWARD_FAILED_PREFIX + what + " forward failed: " + failure.getMessage()));
    }

    @SubscribeEvent
    public static void onMouseButtonPre(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        NeoForgeClientPlatform platform = platformOrNull();
        if (platform == null) {
            return;
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("mouse", () -> ImGuiManager.onMouseButton(handle, event.getButton(),
                    event.getAction(), event.getModifiers()));
        }
        if (platform.imguiWantsMouseForGame()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        NeoForgeClientPlatform platform = platformOrNull();
        if (platform == null) {
            return;
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("scroll", () -> ImGuiManager.onScroll(handle, event.getScrollDeltaX(),
                    event.getScrollDeltaY()));
        }
        if (platform.imguiWantsMouseForGame()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (platformOrNull() == null) {
            return;
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("key", () -> ImGuiManager.onKey(handle, event.getKey(), event.getScanCode(),
                    event.getAction(), event.getModifiers()));
        }
        // NOTE: InputEvent.Key is not cancelable by NeoForge design (the game
        // needs the key state). Typing in an ImGui field while no vanilla
        // screen is open can also drive game bindings (e.g. WASD moves).
        // Prefer short edits while standing still; mouse and scroll ARE gated
        // above, and a capturing-screen wrapper is tracked as future work.
    }

    @SubscribeEvent
    public static void onCharTyped(net.neoforged.neoforge.client.event.ScreenEvent.CharacterTyped.Pre event) {
        NeoForgeClientPlatform platform = platformOrNull();
        if (platform == null) {
            return;
        }
        long handle = windowHandle();
        if (handle != NO_WINDOW_HANDLE) {
            forward("char", () -> ImGuiManager.onChar(handle, event.getCodePoint()));
        }
        if (platform.imguiWantsTextInput()) {
            event.setCanceled(true);
        }
    }
}
