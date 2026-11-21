package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;

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
public final class HeadlessGuiService extends HeadlessGuiWidgetService implements IGuiService {

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
    public void popClip() {
        // Nothing was pushed, so nothing to pop. Silent by design.
    }

    @Override
    public void popPose() {
        // Nothing was pushed, so nothing to pop. Silent by design.
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
