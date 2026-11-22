package com.luatweaker.common.client.luamods;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds a small square shortcut button to the title and pause menus that opens
 * the in-game LuaMod manager (list, details, config, logs, on/off switches).
 */
public final class LuamodMenuButton {
    private LuamodMenuButton() {}

    public static void register() {
        ClientGuiEvent.INIT_POST.register(LuamodMenuButton::onScreenInit);
    }

    /** Square shortcut in the top-left corner; menus rebuild widgets per init. */
    public static void onScreenInit(Screen screen, ScreenAccess access) {
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Button shortcut = Button.builder(Component.literal("LT"),
                        pressed -> minecraft.setScreen(new LuamodListScreen(screen)))
                .bounds(4, 4, 20, 20)
                .tooltip(Tooltip.create(Component.literal("LuaMods manager")))
                .build();
        access.addRenderableWidget(shortcut);
    }
}
