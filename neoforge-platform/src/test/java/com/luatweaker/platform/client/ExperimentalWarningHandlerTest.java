package com.luatweaker.platform.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the button labels Minecraft actually uses for its experimental-features screens. */
class ExperimentalWarningHandlerTest {

    private static final class TestScreen extends Screen {
        TestScreen() {
            super(Component.literal("test"));
        }

        void attach(Button button) {
            addRenderableWidget(button);
        }
    }

    private static Screen screenWith(Button button) {
        TestScreen screen = new TestScreen();
        screen.attach(button);
        return screen;
    }

    @Test
    void mapsEveryExperimentalConfirmationToItsRealConfirmButton() {
        assertEquals(CommonComponents.GUI_PROCEED,
                ExperimentalWarningHandler.confirmLabelFor(
                        new ConfirmExperimentalFeaturesScreen(List.of(), accepted -> { })));

        assertEquals(CommonComponents.GUI_YES,
                ExperimentalWarningHandler.confirmLabelFor(new ConfirmScreen(
                        accepted -> { },
                        Component.translatable("selectWorld.warning.experimental.title"),
                        Component.literal("message"))));

        assertEquals(Component.translatable("selectWorld.backupJoinConfirmButton"),
                ExperimentalWarningHandler.confirmLabelFor(new BackupConfirmScreen(
                        () -> { },
                        (backup, eraseCache) -> { },
                        Component.translatable("selectWorld.backupQuestion.experimental"),
                        Component.literal("message"),
                        false)));
    }

    @Test
    void leavesUnrelatedWarningsAlone() {
        assertNull(ExperimentalWarningHandler.confirmLabelFor(new ConfirmScreen(
                accepted -> { },
                Component.translatable("selectWorld.warning.deprecated.title"),
                Component.literal("message"))));

        assertNull(ExperimentalWarningHandler.confirmLabelFor(new BackupConfirmScreen(
                () -> { },
                (backup, eraseCache) -> { },
                Component.translatable("selectWorld.backupQuestion.customized"),
                Component.literal("message"),
                false)));

        assertNull(ExperimentalWarningHandler.confirmLabelFor(new Screen(Component.literal("plain")) { }));
    }

    @Test
    void pressesOnlyTheMatchingButton() {
        AtomicBoolean proceedPressed = new AtomicBoolean();
        AtomicBoolean yesPressed = new AtomicBoolean();
        TestScreen screen = new TestScreen();
        screen.attach(Button.builder(CommonComponents.GUI_PROCEED, button -> proceedPressed.set(true)).build());
        screen.attach(Button.builder(CommonComponents.GUI_YES, button -> yesPressed.set(true)).build());

        assertTrue(ExperimentalWarningHandler.pressButton(screen, CommonComponents.GUI_PROCEED));
        assertTrue(proceedPressed.get());
        assertFalse(yesPressed.get(), "a different label must not be pressed");
    }

    @Test
    void reportsWhenNoButtonMatches() {
        Screen screen = screenWith(
                Button.builder(CommonComponents.GUI_CANCEL, button -> { }).build());

        assertFalse(ExperimentalWarningHandler.pressButton(screen, CommonComponents.GUI_PROCEED));
    }
}
