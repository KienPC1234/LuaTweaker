package com.luatweaker.platform.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pressing a warning button must run only the matching callback. */
class ExperimentalButtonPressTest {

    private static StubWarningScreen screenWithProceedAndYes(AtomicBoolean proceedPressed,
            AtomicBoolean yesPressed) {
        StubWarningScreen screen = StubWarningScreen.with(
                Button.builder(CommonComponents.GUI_PROCEED, button -> proceedPressed.set(true)).build());
        screen.attach(
                Button.builder(CommonComponents.GUI_YES, button -> yesPressed.set(true)).build());
        return screen;
    }

    @Test
    void pressesTheMatchingButton() {
        StubWarningScreen screen = screenWithProceedAndYes(new AtomicBoolean(), new AtomicBoolean());

        assertTrue(ExperimentalWarningHandler.pressButton(screen, CommonComponents.GUI_PROCEED),
                "pressing must report success when the matching button exists");
    }

    @Test
    void pressingRunsOnlyTheMatchingCallback() {
        AtomicBoolean proceedPressed = new AtomicBoolean();
        AtomicBoolean yesPressed = new AtomicBoolean();
        StubWarningScreen screen = screenWithProceedAndYes(proceedPressed, yesPressed);

        ExperimentalWarningHandler.pressButton(screen, CommonComponents.GUI_PROCEED);

        assertTrue(proceedPressed.get(),
                "the Proceed callback must run when its label is pressed");
    }

    @Test
    void pressingLeavesOtherButtonsUntouched() {
        AtomicBoolean proceedPressed = new AtomicBoolean();
        AtomicBoolean yesPressed = new AtomicBoolean();
        StubWarningScreen screen = screenWithProceedAndYes(proceedPressed, yesPressed);

        ExperimentalWarningHandler.pressButton(screen, CommonComponents.GUI_PROCEED);

        assertFalse(yesPressed.get(),
                "a different label must not be pressed");
    }

    @Test
    void reportsWhenNoButtonMatches() {
        Screen screen = StubWarningScreen.with(
                Button.builder(CommonComponents.GUI_CANCEL, button -> {
                }).build());

        assertFalse(ExperimentalWarningHandler.pressButton(screen, CommonComponents.GUI_PROCEED),
                "pressing must report failure when no button carries the requested label");
    }
}
