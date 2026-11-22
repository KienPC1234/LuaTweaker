package com.luatweaker.common.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Test-only screen that accepts buttons for the warning-handler tests. */
final class StubWarningScreen extends Screen {

    private StubWarningScreen() {
        super(Component.literal("test"));
    }

    static StubWarningScreen with(Button button) {
        StubWarningScreen screen = new StubWarningScreen();
        screen.attach(button);
        return screen;
    }

    void attach(Button button) {
        addRenderableWidget(button);
    }
}
