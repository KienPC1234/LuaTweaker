package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.Nullable;

/**
 * {@link IGuiService} implementation for a dedicated server, where no client runs.
 *
 * <p>Exists so {@code require("LuaTweaker.GuiService")} resolves to a table with the same
 * method surface everywhere. Drawing is impossible without a client, so calls are counted
 * and reported once instead of silently pretending to work. This class deliberately has
 * no Minecraft imports: it must load on a dedicated server, where client classes are absent.</p>
 *
 * <p>The HUD signal ({@code OnRenderHUD}) is not attached here; scripts use its absence to
 * detect a non-client process.</p>
 */
public final class HeadlessGuiService implements IGuiService {

    private boolean ignoredCallReported;

    private void reportIgnored(String method) {
        if (ignoredCallReported) {
            return;
        }
        ignoredCallReported = true;
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "GuiService:" + method + " ignored: this process has no client (further HUD calls"
                        + " stay silent until the next reload)");
    }

    @Override
    public void drawRect(int x, int y, int width, int height, int color) {
        reportIgnored("DrawRect");
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean dropShadow) {
        reportIgnored("DrawText");
    }

    @Override
    public void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow) {
        reportIgnored("DrawTextCentered");
    }

    @Override
    public void drawOutline(int x, int y, int width, int height, int color) {
        reportIgnored("DrawOutline");
    }

    @Override
    public void drawProgressBar(int x, int y, int width, int height, double fillPercent,
                                int backgroundColor, int fillColor) {
        reportIgnored("DrawProgressBar");
    }

    @Override
    public int[] getScreenSize() {
        // Truthful: there is no window. Scripts that branch on a zero size degrade cleanly.
        return new int[] { 0, 0 };
    }

    @Override
    public int[] measureText(String text) {
        return new int[] { 0, 0 };
    }

    @Override
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        reportIgnored("DrawTexture");
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        reportIgnored("PushClip");
    }

    @Override
    public void popClip() {
        // Nothing was pushed, so nothing to pop. Silent by design.
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int color, int thickness) {
        reportIgnored("DrawLine");
    }

    @Override
    public void drawItem(String itemId, int x, int y, int count) {
        reportIgnored("DrawItem");
    }

    @Override
    public void drawGradient(int x, int y, int width, int height, int topColor, int bottomColor) {
        reportIgnored("DrawGradient");
    }

    @Override
    public void drawSprite(String spriteId, int x, int y, int width, int height) {
        reportIgnored("DrawSprite");
    }

    @Override
    public void drawTooltip(String text, int x, int y) {
        reportIgnored("DrawTooltip");
    }

    @Override
    public void pushPose() {
        reportIgnored("PushPose");
    }

    @Override
    public void popPose() {
        // Nothing was pushed, so nothing to pop. Silent by design.
    }

    @Override
    public void translate(double x, double y, double z) {
        reportIgnored("Translate");
    }

    @Override
    public void scale(double x, double y, double z) {
        reportIgnored("Scale");
    }

    @Override
    public void registerScreen(String screenId, @Nullable ILuaTable screenTable) {
        reportIgnored("RegisterScreen");
    }

    @Override
    public void openScreen(String screenId, String title, boolean pauseGame) {
        reportIgnored("OpenScreen");
    }

    @Override
    public void closeScreen() {
        // No screen can be open without a client.
    }

    @Override
    public boolean isScreenOpen() {
        return false;
    }
}
