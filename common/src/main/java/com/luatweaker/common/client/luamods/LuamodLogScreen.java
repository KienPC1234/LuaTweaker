package com.luatweaker.common.client.luamods;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * Read-only viewer for a mod's dedicated log file
 * ({@code logs/luatweaker/mods/<mod>.log}) with responsive full-height bounds.
 */
public class LuamodLogScreen extends Screen {
    private static final int BOX_TOP = 26;

    private final Screen parent;
    private final String modId;

    private MultiLineEditBox viewer;

    public LuamodLogScreen(@Nullable Screen parent, @NotNull String modId) {
        super(Component.literal("Logs: " + modId));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        int rowY = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET;
        int boxWidth = Math.max(100, width - 32);
        int boxHeight = Math.max(36, rowY - BOX_TOP - 6);
        viewer = new ReadOnlyBox(font, width / 2 - boxWidth / 2, BOX_TOP, boxWidth, boxHeight);
        viewer.setCharacterLimit(256_000);
        refresh();
        addRenderableWidget(viewer);

        int bw = LuamodLayouts.buttonWidth(width, 2);
        int left = LuamodLayouts.rowLeft(width, 2, bw);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), pressed -> refresh())
                .bounds(LuamodLayouts.buttonX(left, 0, bw), rowY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), pressed -> onClose())
                .bounds(LuamodLayouts.buttonX(left, 1, bw), rowY, bw, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, title.getString(), Math.max(40, width - 24)),
                width / 2, 10, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void refresh() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        viewer.setValue(String.join("\n",
                LuamodClientActions.readLogTail(gameDir, modId, 300)));
    }

    /** Edit box that keeps scrolling but drops every typed key. */
    private static final class ReadOnlyBox extends MultiLineEditBox {
        ReadOnlyBox(Font font, int x, int y, int width, int height) {
            super(font, x, y, width, height, Component.literal("Logs"), Component.literal("Logs"));
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return keyCode != GLFW.GLFW_KEY_ESCAPE;
        }
    }
}
