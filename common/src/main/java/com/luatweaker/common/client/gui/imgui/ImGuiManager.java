package com.luatweaker.common.client.gui.imgui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Lifecycle and input manager for Dear ImGui integration in Minecraft 1.21.1 LWJGL3 context.
 */
public final class ImGuiManager {

    private static boolean initialized;
    private static ImGuiImplGlfw imGuiGlfw;
    private static ImGuiImplGl3 imGuiGl3;

    private ImGuiManager() {}

    private static final ReentrantLock INIT_LOCK = new ReentrantLock();

    public static void ensureInitialized(long windowHandle) {
        INIT_LOCK.lock();
        try {
            if (initialized) {
                return;
            }

            try {
                ImGui.createContext();
            } catch (LinkageError e) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[ImGui] Native library failed to load, disabling ImGui: " + e.getMessage());
                return;
            }
            ImGuiIO io = ImGui.getIO();
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
            io.setIniFilename("config/luatweaker_imgui.ini");

            // The default font has no Vietnamese glyphs: load a system font with
            // the Vietnamese glyph ranges, falling back to the default (logged)
            // when none is found. Fonts must enter the atlas before backend init.
            byte[] fontBytes = com.luatweaker.common.client.ImGuiFontLoader.loadFontBytes();
            if (fontBytes != null) {
                io.getFonts().addFontFromMemoryTTF(fontBytes,
                        com.luatweaker.common.client.ImGuiFontLoader.FONT_SIZE_PX,
                        io.getFonts().getGlyphRangesVietnamese());
            } else {
                io.getFonts().addFontDefault();
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[ImGui] no Vietnamese-capable font found (tried config/luatweaker_font.ttf"
                        + " + OS system fonts); Vietnamese diacritics will render as '?'.");
            }
            io.getFonts().build();

            applyMinecraftDarkTheme();

            imGuiGlfw = new ImGuiImplGlfw();
            imGuiGl3 = new ImGuiImplGl3();

            // installCallbacks = false so we never overwrite Minecraft's own GLFW callbacks
            imGuiGlfw.init(windowHandle, false);
            imGuiGl3.init();

            initialized = true;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    private static void applyMinecraftDarkTheme() {
        ImGui.styleColorsDark();
        var style = ImGui.getStyle();
        style.setWindowRounding(5.0f);
        style.setFrameRounding(4.0f);
        style.setPopupRounding(4.0f);
        style.setGrabRounding(3.0f);
        style.setScrollbarRounding(4.0f);
        style.setTabRounding(4.0f);
        style.setItemSpacing(8.0f, 6.0f);
        style.setFramePadding(6.0f, 4.0f);

        // Modern Charcoal with Emerald/Gold accents
        style.setColor(ImGuiCol.WindowBg, 0.12f, 0.12f, 0.13f, 0.96f);
        style.setColor(ImGuiCol.Header, 0.20f, 0.22f, 0.24f, 1.0f);
        style.setColor(ImGuiCol.HeaderHovered, 0.26f, 0.30f, 0.34f, 1.0f);
        style.setColor(ImGuiCol.HeaderActive, 0.30f, 0.35f, 0.40f, 1.0f);

        style.setColor(ImGuiCol.Button, 0.18f, 0.20f, 0.22f, 1.0f);
        style.setColor(ImGuiCol.ButtonHovered, 0.24f, 0.28f, 0.32f, 1.0f);
        style.setColor(ImGuiCol.ButtonActive, 0.16f, 0.48f, 0.32f, 1.0f);

        style.setColor(ImGuiCol.FrameBg, 0.16f, 0.16f, 0.18f, 1.0f);
        style.setColor(ImGuiCol.FrameBgHovered, 0.22f, 0.22f, 0.25f, 1.0f);
        style.setColor(ImGuiCol.FrameBgActive, 0.26f, 0.26f, 0.30f, 1.0f);

        style.setColor(ImGuiCol.TitleBg, 0.09f, 0.09f, 0.10f, 1.0f);
        style.setColor(ImGuiCol.TitleBgActive, 0.14f, 0.15f, 0.17f, 1.0f);
        style.setColor(ImGuiCol.CheckMark, 0.20f, 0.85f, 0.45f, 1.0f);
        style.setColor(ImGuiCol.SliderGrab, 0.22f, 0.75f, 0.42f, 1.0f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.28f, 0.90f, 0.50f, 1.0f);
    }

    public static void startFrame(int displayWidth, int displayHeight) {
        startFrame(displayWidth, displayHeight, 1.0f, 1.0f);
    }

    public static void startFrame(int displayWidth, int displayHeight, float framebufferScaleX, float framebufferScaleY) {
        startFrame(displayWidth, displayHeight, framebufferScaleX, framebufferScaleY, -1, -1);
    }

    public static void startFrame(int displayWidth, int displayHeight, float framebufferScaleX, float framebufferScaleY, double mouseX, double mouseY) {
        if (!initialized) { return; }

        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();

        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(displayWidth, displayHeight);
        io.setDisplayFramebufferScale(framebufferScaleX, framebufferScaleY);
        updateMousePos(io, mouseX, mouseY);
        ImGui.newFrame();
    }

    private static void updateMousePos(ImGuiIO io, double mouseX, double mouseY) {
        if (mouseX >= 0 && mouseY >= 0) {
            io.setMousePos((float) mouseX, (float) mouseY);
        }
    }

    public static void endFrame() {
        if (!initialized) { return; }

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    public static void onMouseButton(long window, int button, int action, int mods) {
        if (initialized && imGuiGlfw != null) {
            imGuiGlfw.mouseButtonCallback(window, button, action, mods);
        }
    }

    public static void onCursorPos(long window, double xpos, double ypos) {
        if (initialized && imGuiGlfw != null) {
            imGuiGlfw.cursorPosCallback(window, xpos, ypos);
        }
    }

    public static void onScroll(long window, double xoffset, double yoffset) {
        if (initialized && imGuiGlfw != null) {
            imGuiGlfw.scrollCallback(window, xoffset, yoffset);
        }
    }

    public static void onKey(long window, int key, int scancode, int action, int mods) {
        if (initialized && imGuiGlfw != null) {
            imGuiGlfw.keyCallback(window, key, scancode, action, mods);
        }
    }

    public static void onChar(long window, int codepoint) {
        if (initialized && imGuiGlfw != null) {
            imGuiGlfw.charCallback(window, codepoint);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void syncMousePos(double mouseX, double mouseY) {
        if (initialized) {
            ImGui.getIO().setMousePos((float) mouseX, (float) mouseY);
        }
    }

    public static boolean wantCaptureMouse() {
        return initialized && ImGui.getIO().getWantCaptureMouse();
    }

    public static boolean wantCaptureKeyboard() {
        return initialized && ImGui.getIO().getWantCaptureKeyboard();
    }

    public static void destroy() {
        INIT_LOCK.lock();
        try {
            if (!initialized) { return; }
            if (imGuiGl3 != null) {
                imGuiGl3.shutdown();
            }
            if (imGuiGlfw != null) {
                imGuiGlfw.shutdown();
            }
            ImGui.destroyContext();
            // initialized=false is the publish gate: ensureInitialized replaces both backend
            // instances before any other path can touch them again.
            initialized = false;
        } finally {
            INIT_LOCK.unlock();
        }
    }
}
