package com.luatweaker.common.client.gui.imgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Transparent overlay screen providing full mouse cursor interaction and camera escape
 * for Lua-driven Dear ImGui windows.
 */
public class ImGuiOverlayScreen extends Screen {

    private static @Nullable Consumer<GuiGraphics> frameRenderer;

    public ImGuiOverlayScreen() {
        super(Component.literal("LuaTweaker ImGui Overlay"));
    }

    public static void setFrameRenderer(@Nullable Consumer<GuiGraphics> renderer) {
        frameRenderer = renderer;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0x20000000, 0x40000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        syncCursor();
        if (frameRenderer != null) {
            frameRenderer.accept(graphics);
        }
    }

    private static void syncCursor() {
        if (Minecraft.getInstance().mouseHandler != null) {
            ImGuiManager.syncMousePos(Minecraft.getInstance().mouseHandler.xpos(),
                    Minecraft.getInstance().mouseHandler.ypos());
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        syncCursor();
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        syncCursor();
        return ImGuiManager.wantCaptureMouse() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncCursor();
        return ImGuiManager.wantCaptureMouse() || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncCursor();
        return ImGuiManager.wantCaptureMouse() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncCursor();
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
}
