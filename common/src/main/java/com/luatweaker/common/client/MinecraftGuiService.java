package com.luatweaker.common.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-agnostic GUI rendering service using Minecraft's GuiGraphics.
 */
public class MinecraftGuiService implements IGuiService {
    private static final ThreadLocal<GuiGraphics> CURRENT_GRAPHICS = new ThreadLocal<>();
    private static int clipDepth;
    private static int poseDepth;

    private final Map<String, ILuaTable> screenRegistry = new ConcurrentHashMap<>();

    public static void setGraphics(GuiGraphics graphics) {
        CURRENT_GRAPHICS.set(graphics);
        clipDepth = 0;
        poseDepth = 0;
    }

    public static void clearGraphics() {
        GuiGraphics graphics = CURRENT_GRAPHICS.get();
        if (graphics != null) {
            while (clipDepth > 0) {
                graphics.disableScissor();
                clipDepth--;
            }
            while (poseDepth > 0) {
                graphics.pose().popPose();
                poseDepth--;
            }
        }
        CURRENT_GRAPHICS.remove();
        clipDepth = 0;
        poseDepth = 0;
    }

    @Override
    public void drawRect(int x, int y, int width, int height, int color) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.fill(x, y, x + width, y + height, color);
        }
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean dropShadow) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.drawString(Minecraft.getInstance().font, text, x, y, color, dropShadow);
        }
    }

    @Override
    public void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            int textWidth = Minecraft.getInstance().font.width(text);
            g.drawString(Minecraft.getInstance().font, text, centerX - textWidth / 2, y, color, dropShadow);
        }
    }

    @Override
    public void drawOutline(int x, int y, int width, int height, int color) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.fill(x, y, x + width, y + 1, color);
            g.fill(x, y + height - 1, x + width, y + height, color);
            g.fill(x, y, x + 1, y + height, color);
            g.fill(x + width - 1, y, x + width, y + height, color);
        }
    }

    @Override
    public void drawProgressBar(int x, int y, int width, int height, double fillPercent, int backgroundColor, int fillColor) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
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
    public int[] measureText(String text) {
        if (text == null || text.isEmpty()) {
            return new int[] { 0, Minecraft.getInstance().font.lineHeight };
        }
        return new int[] { Minecraft.getInstance().font.width(text), Minecraft.getInstance().font.lineHeight };
    }

    @Override
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
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

    @Override
    public void drawTextureRegion(String textureId, int x, int y, int u, int v, int width, int height,
                                  int textureWidth, int textureHeight) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g == null || textureId == null || textureId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(textureId);
        if (location == null) {
            return;
        }
        g.blit(location, x, y, width, height, (float) u, (float) v, width, height, textureWidth, textureHeight);
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g == null) {
            return;
        }
        int right = x + Math.max(0, width);
        int bottom = y + Math.max(0, height);
        g.enableScissor(x, y, right, bottom);
        clipDepth++;
    }

    @Override
    public void popClip() {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g == null || clipDepth <= 0) {
            return;
        }
        g.disableScissor();
        clipDepth--;
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int color, int thickness) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g == null) {
            return;
        }
        int size = Math.max(1, thickness);
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int stepX = x1 < x2 ? 1 : -1;
        int stepY = y1 < y2 ? 1 : -1;
        int error = dx - dy;
        int x = x1;
        int y = y1;
        while (true) {
            g.fill(x, y, x + size, y + size, color);
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

    @Override
    public void drawGradient(int x, int y, int width, int height, int topColor, int bottomColor) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
        }
    }

    @Override
    public void drawSprite(String spriteId, int x, int y, int width, int height) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
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

    @Override
    public void drawTooltip(String text, int x, int y) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null && text != null) {
            g.renderTooltip(Minecraft.getInstance().font, Component.literal(text), x, y);
        }
    }

    @Override
    public void pushPose() {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.pose().pushPose();
            poseDepth++;
        }
    }

    @Override
    public void popPose() {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null && poseDepth > 0) {
            g.pose().popPose();
            poseDepth--;
        }
    }

    @Override
    public void translate(double x, double y, double z) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.pose().translate((float) x, (float) y, (float) z);
        }
    }

    @Override
    public void scale(double x, double y, double z) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
        if (g != null) {
            g.pose().scale((float) x, (float) y, (float) z);
        }
    }

    @Override
    public void drawItem(String itemId, int x, int y, int count) {
        GuiGraphics g = CURRENT_GRAPHICS.get();
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

    @Override
    public void registerScreen(String screenId, @Nullable ILuaTable screenTable) {
        if (screenId != null && screenTable != null) {
            screenRegistry.put(screenId, screenTable);
        }
    }

    @Override
    public void openScreen(String screenId, String title, boolean pauseGame) {
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "GuiService:OpenScreen('" + screenId + "') ignored on a dedicated server");
            return;
        }
        ILuaTable screenTable = screenRegistry.get(screenId);
        ILuaEngine engine = null;
        client.openScreen(screenId, title, screenTable, engine, pauseGame);
    }

    @Override
    public void closeScreen() {
        IPlatformClient client = Platform.getClientOrNull();
        if (client != null) {
            client.closeScreen();
        }
    }

    @Override
    public boolean isScreenOpen() {
        IPlatformClient client = Platform.getClientOrNull();
        return client != null && client.isScreenOpen();
    }
}
