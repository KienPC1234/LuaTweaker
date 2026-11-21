package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.Nullable;

/**
 * Headless widget/clip/pose half of {@link IGuiService}. Split from the drawing half so each
 * collaborator stays small; both only report the first ignored call.
 */
abstract class HeadlessGuiWidgetService extends HeadlessGuiDrawingService implements IGuiService {

    @Override
    public void pushClip(int x, int y, int width, int height) {
        reportIgnored("PushClip");
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
}
