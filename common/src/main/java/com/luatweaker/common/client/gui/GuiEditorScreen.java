package com.luatweaker.common.client.gui;

import com.luatweaker.common.client.gui.imgui.ImGuiGuiEditor;
import com.luatweaker.common.client.gui.imgui.ImGuiManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Screen bridge hosting the Dear ImGui powered visual GUI editor.
 * Provides responsive full-screen workspace, interactive canvas, live code export,
 * and seamless event dispatching within Minecraft 1.21.1.
 */
public class GuiEditorScreen extends Screen {

    private final String blockName;
    private final int panelWidth;
    private final int panelHeight;
    private final boolean isJeiMode;
    private ImGuiGuiEditor imguiEditor;

    public GuiEditorScreen(String blockName, int panelWidth, int panelHeight, boolean isJeiMode) {
        super(Component.literal("LuaTweaker GUI Editor: " + blockName));
        this.blockName = blockName;
        this.panelWidth = panelWidth > 0 ? panelWidth : isJeiMode ? 150 : 176;
        this.panelHeight = panelHeight > 0 ? panelHeight : isJeiMode ? 65 : 166;
        this.isJeiMode = isJeiMode;
    }

    public GuiEditorScreen(String blockName, int panelWidth, int panelHeight) {
        this(blockName, panelWidth, panelHeight, false);
    }

    public GuiEditorScreen(String blockName) {
        this(blockName, 176, 166, false);
    }

    @Override
    protected void init() {
        super.init();
        if (Minecraft.getInstance().getWindow() != null) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            ImGuiManager.ensureInitialized(window);
        }
        if (imguiEditor == null) {
            imguiEditor = new ImGuiGuiEditor(blockName, panelWidth, panelHeight, isJeiMode);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background behind ImGui workspace
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x90000000, 0xD0000000);

        if (Minecraft.getInstance().getWindow() != null) {
            com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
            int screenW = window.getScreenWidth();
            int screenH = window.getScreenHeight();
            float fbScaleX = screenW > 0 ? (float) window.getWidth() / screenW : 1.0f;
            float fbScaleY = screenH > 0 ? (float) window.getHeight() / screenH : 1.0f;
            double cursorX = Minecraft.getInstance().mouseHandler != null ? Minecraft.getInstance().mouseHandler.xpos() : -1;
            double cursorY = Minecraft.getInstance().mouseHandler != null ? Minecraft.getInstance().mouseHandler.ypos() : -1;

            ImGuiManager.startFrame(screenW, screenH, fbScaleX, fbScaleY, cursorX, cursorY);
            if (imguiEditor != null) {
                imguiEditor.render();
            }
            ImGuiManager.endFrame();
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (Minecraft.getInstance().mouseHandler != null) {
            ImGuiManager.syncMousePos(Minecraft.getInstance().mouseHandler.xpos(),
                    Minecraft.getInstance().mouseHandler.ypos());
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (Minecraft.getInstance().mouseHandler != null) {
            ImGuiManager.syncMousePos(Minecraft.getInstance().mouseHandler.xpos(),
                    Minecraft.getInstance().mouseHandler.ypos());
        }
        return ImGuiManager.wantCaptureMouse() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().mouseHandler != null) {
            ImGuiManager.syncMousePos(Minecraft.getInstance().mouseHandler.xpos(),
                    Minecraft.getInstance().mouseHandler.ypos());
        }
        return ImGuiManager.wantCaptureMouse() || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().mouseHandler != null) {
            ImGuiManager.syncMousePos(Minecraft.getInstance().mouseHandler.xpos(),
                    Minecraft.getInstance().mouseHandler.ypos());
        }
        return ImGuiManager.wantCaptureMouse() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Minecraft.getInstance().mouseHandler != null) {
            ImGuiManager.syncMousePos(Minecraft.getInstance().mouseHandler.xpos(),
                    Minecraft.getInstance().mouseHandler.ypos());
        }
        return ImGuiManager.wantCaptureMouse() || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().getWindow() != null) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            ImGuiManager.onKey(window, keyCode, scanCode, GLFW.GLFW_PRESS, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        return ImGuiManager.wantCaptureKeyboard() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().getWindow() != null) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            ImGuiManager.onKey(window, keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
        }
        return ImGuiManager.wantCaptureKeyboard() || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (Minecraft.getInstance().getWindow() != null) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            ImGuiManager.onChar(window, codePoint);
        }
        return ImGuiManager.wantCaptureKeyboard() || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
