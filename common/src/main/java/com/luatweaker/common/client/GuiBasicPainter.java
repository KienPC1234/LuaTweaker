package com.luatweaker.common.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Basic rectangle, text, tooltip, and pose transforms for {@link MinecraftGuiService}.
 */
final class GuiBasicPainter {

    private GuiBasicPainter() {
    }

    static void drawRect(@Nullable GuiGraphics g, int x, int y, int width, int height, int color) {
        if (g != null && width > 0 && height > 0) {
            g.fill(x, y, x + width, y + height, GuiFrameState.opaqueColor(color));
        }
    }

    static void drawText(@Nullable GuiGraphics g, @Nullable String text, int x, int y, int color, boolean dropShadow) {
        if (g != null && text != null) {
            g.drawString(Minecraft.getInstance().font, text, x, y, GuiFrameState.opaqueColor(color), dropShadow);
        }
    }

    static void drawTextCentered(@Nullable GuiGraphics g, @Nullable String text,
                                 int centerX, int y, int color, boolean dropShadow) {
        if (g != null && text != null) {
            int textWidth = Minecraft.getInstance().font.width(text);
            g.drawString(Minecraft.getInstance().font, text, centerX - textWidth / 2, y,
                    GuiFrameState.opaqueColor(color), dropShadow);
        }
    }

    static void drawOutline(@Nullable GuiGraphics g, int x, int y, int width, int height, int color) {
        if (g != null && width > 0 && height > 0) {
            int argb = GuiFrameState.opaqueColor(color);
            g.fill(x, y, x + width, y + 1, argb);
            g.fill(x, y + height - 1, x + width, y + height, argb);
            g.fill(x, y, x + 1, y + height, argb);
            g.fill(x + width - 1, y, x + width, y + height, argb);
        }
    }

    static void drawGradient(@Nullable GuiGraphics g, int x, int y, int width, int height,
                             int topColor, int bottomColor) {
        if (g != null && width > 0 && height > 0) {
            g.fillGradient(x, y, x + width, y + height,
                    GuiFrameState.opaqueColor(topColor), GuiFrameState.opaqueColor(bottomColor));
        }
    }

    static void drawTooltip(@Nullable GuiGraphics g, @Nullable String text, int x, int y) {
        if (g != null && text != null) {
            g.renderTooltip(Minecraft.getInstance().font, Component.literal(text), x, y);
        }
    }

    static int[] measureText(@Nullable String text) {
        int w = text == null || text.isEmpty() ? 0 : Minecraft.getInstance().font.width(text);
        return new int[] { w, Minecraft.getInstance().font.lineHeight };
    }

    static void translate(@Nullable GuiGraphics g, double x, double y, double z) {
        if (g != null) {
            g.pose().translate((float) x, (float) y, (float) z);
        }
    }

    static void scale(@Nullable GuiGraphics g, double x, double y, double z) {
        if (g != null) {
            g.pose().scale((float) x, (float) y, (float) z);
        }
    }
}
