package com.luatweaker.platform.client;

import com.luatweaker.platform.client.gui.GuiEditorScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-only bridge for opening screens. Safe from dedicated server ClassNotFound.
 */
public final class ClientGuiOpener {

    private ClientGuiOpener() {}

    public static void openGuiEditor(String title, int width, int height) {
        openGuiEditor(title, width, height, false);
    }

    public static void openGuiEditor(String title, int width, int height, boolean isJeiMode) {
        Minecraft.getInstance().setScreen(new GuiEditorScreen(title, width, height, isJeiMode));
    }
}
