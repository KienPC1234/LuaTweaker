package com.luatweaker.common.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.common.LuaTweakerCommon;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-agnostic GUI rendering service using Minecraft's GuiGraphics.
 */
public class MinecraftGuiService implements IGuiService {

    private final Map<String, ILuaTable> screenRegistry = new ConcurrentHashMap<>();

    public static void setGraphics(GuiGraphics graphics) {
        GuiFrameState.bind(graphics);
    }

    public static void clearGraphics() {
        GuiFrameState.clear();
    }

    @Override
    public void drawRect(int x, int y, int width, int height, int color) {
        GuiBasicPainter.drawRect(GuiFrameState.currentGraphics(), x, y, width, height, color);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean dropShadow) {
        GuiBasicPainter.drawText(GuiFrameState.currentGraphics(), text, x, y, color, dropShadow);
    }

    @Override
    public void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow) {
        GuiBasicPainter.drawTextCentered(GuiFrameState.currentGraphics(), text, centerX, y, color, dropShadow);
    }

    @Override
    public void drawOutline(int x, int y, int width, int height, int color) {
        GuiBasicPainter.drawOutline(GuiFrameState.currentGraphics(), x, y, width, height, color);
    }

    @Override
    public void drawProgressBar(int x, int y, int width, int height, double fillPercent,
                                int backgroundColor, int fillColor) {
        GuiCanvasPainter.drawProgressBar(GuiFrameState.currentGraphics(), x, y, width, height,
                fillPercent, backgroundColor, fillColor);
    }

    @Override
    public int[] getScreenSize() {
        Window window = Minecraft.getInstance().getWindow();
        return new int[] { window.getGuiScaledWidth(), window.getGuiScaledHeight() };
    }

    @Override
    public int[] measureText(String text) {
        return GuiBasicPainter.measureText(text);
    }

    @Override
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        GuiCanvasPainter.drawTexture(GuiFrameState.currentGraphics(), textureId, x, y, width, height);
    }

    @Override
    public void drawTextureRegion(String textureId, int x, int y, int u, int v, int width, int height,
                                  int textureWidth, int textureHeight) {
        GuiCanvasPainter.drawTextureRegion(GuiFrameState.currentGraphics(), textureId, x, y, u, v,
                width, height, textureWidth, textureHeight);
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        GuiFrameState.pushClip(x, y, width, height);
    }

    @Override
    public void popClip() {
        GuiFrameState.popClip();
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int color, int thickness) {
        GuiCanvasPainter.drawLine(GuiFrameState.currentGraphics(), x1, y1, x2, y2, color, thickness);
    }

    @Override
    public void drawGradient(int x, int y, int width, int height, int topColor, int bottomColor) {
        GuiBasicPainter.drawGradient(GuiFrameState.currentGraphics(), x, y, width, height, topColor, bottomColor);
    }

    @Override
    public void drawSprite(String spriteId, int x, int y, int width, int height) {
        GuiCanvasPainter.drawSprite(GuiFrameState.currentGraphics(), spriteId, x, y, width, height);
    }

    @Override
    public void drawTooltip(String text, int x, int y) {
        GuiBasicPainter.drawTooltip(GuiFrameState.currentGraphics(), text, x, y);
    }

    @Override
    public void pushPose() {
        GuiFrameState.pushPose();
    }

    @Override
    public void popPose() {
        GuiFrameState.popPose();
    }

    @Override
    public void translate(double x, double y, double z) {
        GuiBasicPainter.translate(GuiFrameState.currentGraphics(), x, y, z);
    }

    @Override
    public void scale(double x, double y, double z) {
        GuiBasicPainter.scale(GuiFrameState.currentGraphics(), x, y, z);
    }

    @Override
    public void drawItem(String itemId, int x, int y, int count) {
        GuiCanvasPainter.drawItem(GuiFrameState.currentGraphics(), itemId, x, y, count);
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
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "GuiService:OpenScreen('" + screenId + "') ignored on a dedicated server");
            return;
        }
        ILuaTable screenTable = screenRegistry.get(screenId);
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
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
