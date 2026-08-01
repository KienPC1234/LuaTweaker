package com.luatweaker.platform.client;

import com.luatweaker.api.client.IGuiService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

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
}
