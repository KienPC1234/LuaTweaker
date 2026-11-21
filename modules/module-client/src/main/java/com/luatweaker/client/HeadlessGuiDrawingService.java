package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;

/**
 * Dedicated-server {@link IGuiService} base: counts the first ignored draw call and reports
 * it once, so scripts see the same method surface without pretending drawing works.
 */
abstract class HeadlessGuiDrawingService implements IGuiService {

    private boolean ignoredCallReported;

    protected final void reportIgnored(String method) {
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
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        reportIgnored("DrawTexture");
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
}
