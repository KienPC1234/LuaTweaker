package com.luatweaker.platform.client;

import com.luatweaker.api.client.IGuiService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public class NeoForgeGuiService implements IGuiService {
    private static final ThreadLocal<GuiGraphics> currentGraphics = new ThreadLocal<>();

    public static void setGraphics(GuiGraphics graphics) {
        currentGraphics.set(graphics);
    }

    public static void clearGraphics() {
        currentGraphics.remove();
    }

    @Override
    public void drawRect(int x, int y, int width, int height, int color) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.fill(x, y, x + width, y + height, color);
        }
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean dropShadow) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.drawString(Minecraft.getInstance().font, text, x, y, color, dropShadow);
        }
    }

    @Override
    public void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            int textWidth = Minecraft.getInstance().font.width(text);
            g.drawString(Minecraft.getInstance().font, text, centerX - textWidth / 2, y, color, dropShadow);
        }
    }

    @Override
    public void drawOutline(int x, int y, int width, int height, int color) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.fill(x, y, x + width, y + 1, color);
            g.fill(x, y + height - 1, x + width, y + height, color);
            g.fill(x, y, x + 1, y + height, color);
            g.fill(x + width - 1, y, x + width, y + height, color);
        }
    }

    @Override
    public void drawProgressBar(int x, int y, int width, int height, double fillPercent, int backgroundColor, int fillColor) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.fill(x, y, x + width, y + height, backgroundColor);
            double clamped = Math.clamp(fillPercent, 0.0, 1.0);
            int fillWidth = (int) Math.round(width * clamped);
            if (fillWidth > 0) {
                g.fill(x, y, x + fillWidth, y + height, fillColor);
            }
        }
    }

    @Override
    public int[] getScreenSize() {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        return new int[] { window.getGuiScaledWidth(), window.getGuiScaledHeight() };
    }

    @Override
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        GuiGraphics g = currentGraphics.get();
        if (g == null || textureId == null || textureId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.parse(textureId);
        RenderSystem.setShaderTexture(0, location);
        g.blit(location, x, y, width, height, 0.0F, 0.0F, width, height, width, height);
    }
}
