package com.luatweaker.common.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Low-level 2D drawing primitives delegated by {@link MinecraftGuiService}.
 */
final class GuiCanvasPainter {
    private static final double MIN_PROGRESS = 0.0;
    private static final double MAX_PROGRESS = 1.0;

    private GuiCanvasPainter() {
    }

    static void drawProgressBar(@Nullable GuiGraphics g, int x, int y, int width, int height,
                                double fillPercent, int backgroundColor, int fillColor) {
        if (g == null || width <= 0 || height <= 0) {
            return;
        }
        g.fill(x, y, x + width, y + height, GuiFrameState.opaqueColor(backgroundColor));
        double clamped = Math.clamp(fillPercent, MIN_PROGRESS, MAX_PROGRESS);
        int fillWidth = (int) Math.round(width * clamped);
        if (fillWidth > 0) {
            g.fill(x, y, x + fillWidth, y + height, GuiFrameState.opaqueColor(fillColor));
        }
    }

    static void drawTexture(@Nullable GuiGraphics g, @Nullable String textureId,
                            int x, int y, int width, int height) {
        if (g == null || textureId == null || textureId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(textureId);
        if (location == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "DrawTexture skipped: malformed texture id '" + textureId + "'");
            return;
        }
        g.blit(location, x, y, width, height, 0.0F, 0.0F, width, height, width, height);
    }

    static void drawTextureRegion(@Nullable GuiGraphics g, @Nullable String textureId,
                                  int x, int y, int u, int v, int width, int height,
                                  int textureWidth, int textureHeight) {
        if (g == null || textureId == null || textureId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(textureId);
        if (location != null) {
            g.blit(location, x, y, width, height, (float) u, (float) v, width, height, textureWidth, textureHeight);
        }
    }

    static void drawSprite(@Nullable GuiGraphics g, @Nullable String spriteId,
                           int x, int y, int width, int height) {
        if (g == null || spriteId == null || spriteId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(spriteId);
        if (location == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "DrawSprite skipped: malformed sprite id '" + spriteId + "'");
            return;
        }
        g.blitSprite(location, x, y, width, height);
    }

    static void drawLine(@Nullable GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness) {
        if (g == null) {
            return;
        }
        int argb = GuiFrameState.opaqueColor(color);
        int size = Math.max(1, thickness);
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int stepX = Integer.signum(x2 - x1);
        int stepY = Integer.signum(y2 - y1);
        int error = dx - dy;
        int x = x1;
        int y = y1;
        while (true) {
            g.fill(x, y, x + size, y + size, argb);
            if (x == x2 && y == y2) {
                return;
            }
            int doubled = 2 * error;
            if (doubled > -dy) {
                error -= dy;
                x += stepX;
            }
            if (doubled < dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    static void drawItem(@Nullable GuiGraphics g, @Nullable String itemId, int x, int y, int count) {
        if (g == null || itemId == null || itemId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        Item item = location == null ? null : BuiltInRegistries.ITEM.get(location);
        if (item == null || item == Items.AIR) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM, "DrawItem skipped: unknown item '" + itemId + "'");
            return;
        }
        if (count <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM, "DrawItem skipped: count must be >= 1 (got " + count + ")");
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        g.renderItem(stack, x, y);
        g.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
    }
}
