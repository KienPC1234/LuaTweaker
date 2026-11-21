package com.luatweaker.platform.client.luamods;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds a small square shortcut button to the title and pause menus that opens
 * the in-game LuaMod manager (list, details, config, logs, on/off switches).
 */
public final class LuamodMenuButton {
    private LuamodMenuButton() {}

    /** Square shortcut in the top-left corner; menus rebuild widgets per init. */
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Button shortcut = Button.builder(Component.literal("LT"),
                        pressed -> minecraft.setScreen(new LuamodListScreen(screen)))
                .bounds(4, 4, 20, 20)
                .tooltip(Tooltip.create(Component.literal("LuaMods manager")))
                .build();
        event.addListener(shortcut);
    }
}
