package com.luatweaker.platform.client;

import com.luatweaker.api.client.IGuiService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public class NeoForgeGuiService implements IGuiService {
    private static final ThreadLocal<GuiGraphics> currentGraphics = new ThreadLocal<>();
    private static int clipDepth;
    private static int poseDepth;

    public static void setGraphics(GuiGraphics graphics) {
        currentGraphics.set(graphics);
        // A script that forgot popClip()/popPose() must not leak into the next frame.
        clipDepth = 0;
        poseDepth = 0;
    }

    public static void clearGraphics() {
        GuiGraphics graphics = currentGraphics.get();
        if (graphics != null) {
            while (clipDepth > 0) {
                graphics.disableScissor();
                clipDepth--;
            }
            // Restore the shared pose stack: vanilla and the flash overlay draw after the
            // HUD callbacks on the same GuiGraphics instance.
            while (poseDepth > 0) {
                graphics.pose().popPose();
                poseDepth--;
            }
        }
        currentGraphics.remove();
        clipDepth = 0;
        poseDepth = 0;
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
    public int[] measureText(String text) {
        if (text == null || text.isEmpty()) {
            return new int[] { 0, Minecraft.getInstance().font.lineHeight };
        }
        return new int[] { Minecraft.getInstance().font.width(text), Minecraft.getInstance().font.lineHeight };
    }

    @Override
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        GuiGraphics g = currentGraphics.get();
        if (g == null || textureId == null || textureId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(textureId);
        if (location == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "DrawTexture skipped: malformed texture id '" + textureId + "'");
            return;
        }
        g.blit(location, x, y, width, height, 0.0F, 0.0F, width, height, width, height);
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        GuiGraphics g = currentGraphics.get();
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
        GuiGraphics g = currentGraphics.get();
        if (g == null || clipDepth <= 0) {
            return;
        }
        g.disableScissor();
        clipDepth--;
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int color, int thickness) {
        GuiGraphics g = currentGraphics.get();
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
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
        }
    }

    @Override
    public void drawSprite(String spriteId, int x, int y, int width, int height) {
        GuiGraphics g = currentGraphics.get();
        if (g == null || spriteId == null || spriteId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(spriteId);
        if (location == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "DrawSprite skipped: malformed sprite id '" + spriteId + "'");
            return;
        }
        g.blitSprite(location, x, y, width, height);
    }

    @Override
    public void drawTooltip(String text, int x, int y) {
        GuiGraphics g = currentGraphics.get();
        if (g != null && text != null) {
            g.renderTooltip(Minecraft.getInstance().font,
                    net.minecraft.network.chat.Component.literal(text), x, y);
        }
    }

    @Override
    public void pushPose() {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.pose().pushPose();
            poseDepth++;
        }
    }

    @Override
    public void popPose() {
        GuiGraphics g = currentGraphics.get();
        if (g != null && poseDepth > 0) {
            g.pose().popPose();
            poseDepth--;
        }
    }

    @Override
    public void translate(double x, double y, double z) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.pose().translate((float) x, (float) y, (float) z);
        }
    }

    @Override
    public void scale(double x, double y, double z) {
        GuiGraphics g = currentGraphics.get();
        if (g != null) {
            g.pose().scale((float) x, (float) y, (float) z);
        }
    }

    @Override
    public void drawItem(String itemId, int x, int y, int count) {
        GuiGraphics g = currentGraphics.get();
        if (g == null || itemId == null || itemId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        net.minecraft.world.item.Item item = location == null
                ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "DrawItem skipped: unknown item '" + itemId + "'");
            return;
        }
        if (count <= 0) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "DrawItem skipped: count must be >= 1 (got " + count + ")");
            return;
        }
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(item, count);
        g.renderItem(stack, x, y);
        g.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
    }

    private final java.util.Map<String, com.luatweaker.api.vm.ILuaTable> screenRegistry = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void registerScreen(String screenId, @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaTable screenTable) {
        if (screenId != null && screenTable != null) {
            screenRegistry.put(screenId, screenTable);
        }
    }

    @Override
    public void openScreen(String screenId, String title, boolean pauseGame) {
        com.luatweaker.api.pal.IPlatformClient client = com.luatweaker.api.pal.Platform.getClientOrNull();
        if (client == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "GuiService:OpenScreen('" + screenId + "') ignored on a dedicated server");
            return;
        }
        com.luatweaker.api.vm.ILuaTable screenTable = screenRegistry.get(screenId);
        com.luatweaker.api.vm.ILuaEngine engine = com.luatweaker.platform.LuaTweakerMod.getActiveEngine();
        client.openScreen(screenId, title, screenTable, engine, pauseGame);
    }

    @Override
    public void closeScreen() {
        com.luatweaker.api.pal.IPlatformClient client = com.luatweaker.api.pal.Platform.getClientOrNull();
        if (client != null) {
            client.closeScreen();
        }
    }

    @Override
    public boolean isScreenOpen() {
        com.luatweaker.api.pal.IPlatformClient client = com.luatweaker.api.pal.Platform.getClientOrNull();
        return client != null && client.isScreenOpen();
    }
}
