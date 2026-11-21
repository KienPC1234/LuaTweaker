package com.luatweaker.platform.client.gui;

import com.luatweaker.platform.client.gui.imgui.ImGuiGuiEditor;
import com.luatweaker.platform.client.gui.imgui.ImGuiManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Screen bridge hosting the Dear ImGui powered visual GUI editor.
 * Provides multi-window workspace, interactive canvas, live code export,
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
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.ensureInitialized(window);
        if (imguiEditor == null) {
            imguiEditor = new ImGuiGuiEditor(blockName, panelWidth, panelHeight, isJeiMode);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background behind ImGui workspace
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x90000000, 0xD0000000);

        int fbWidth = Minecraft.getInstance().getWindow().getWidth();
        int fbHeight = Minecraft.getInstance().getWindow().getHeight();

        ImGuiManager.startFrame(fbWidth, fbHeight);
        if (imguiEditor != null) {
            imguiEditor.render();
        }
        ImGuiManager.endFrame();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.onMouseButton(window, button, GLFW.GLFW_PRESS, 0);
        return ImGuiManager.wantCaptureMouse() || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.onMouseButton(window, button, GLFW.GLFW_RELEASE, 0);
        return ImGuiManager.wantCaptureMouse() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.onScroll(window, scrollX, scrollY);
        return ImGuiManager.wantCaptureMouse() || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.onKey(window, keyCode, scanCode, GLFW.GLFW_PRESS, modifiers);

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        return ImGuiManager.wantCaptureKeyboard() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.onKey(window, keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
        return ImGuiManager.wantCaptureKeyboard() || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        ImGuiManager.onChar(window, codePoint);
        return ImGuiManager.wantCaptureKeyboard() || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
