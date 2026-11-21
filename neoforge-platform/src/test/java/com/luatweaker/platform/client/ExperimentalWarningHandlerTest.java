package com.luatweaker.platform.client;

import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Guards the button labels Minecraft actually uses for its experimental-features screens. */
class ExperimentalWarningHandlerTest {

    private static final String MESSAGE_TEXT = "message";

    @Test
    void mapsExperimentalFeaturesScreenToProceed() {
        assertEquals(CommonComponents.GUI_PROCEED,
                ExperimentalWarningHandler.confirmLabelFor(
                        new ConfirmExperimentalFeaturesScreen(List.of(), accepted -> {
                        })),
                "the experimental-features gate must map to its Proceed button");
    }

    @Test
    void mapsWarningConfirmScreenToYes() {
        assertEquals(CommonComponents.GUI_YES,
                ExperimentalWarningHandler.confirmLabelFor(new ConfirmScreen(
                        accepted -> {
                        },
                        Component.translatable("selectWorld.warning.experimental.title"),
                        Component.literal(MESSAGE_TEXT))),
                "the generic warning confirm screen must map to its Yes button");
    }

    @Test
    void mapsBackupConfirmScreenToBackupButton() {
        assertEquals(Component.translatable("selectWorld.backupJoinConfirmButton"),
                ExperimentalWarningHandler.confirmLabelFor(new BackupConfirmScreen(
                        () -> {
                        },
                        (backup, eraseCache) -> {
                        },
                        Component.translatable("selectWorld.backupQuestion.experimental"),
                        Component.literal(MESSAGE_TEXT),
                        false)),
                "the backup confirm screen must map to its backup-join button");
    }

    @Test
    void leavesDeprecatedWarningAlone() {
        assertNull(ExperimentalWarningHandler.confirmLabelFor(new ConfirmScreen(
                        accepted -> {
                        },
                        Component.translatable("selectWorld.warning.deprecated.title"),
                        Component.literal(MESSAGE_TEXT))),
                "a deprecated-warning screen is not an experimental gate and must pass through");
    }

    @Test
    void leavesCustomizedBackupQuestionAlone() {
        assertNull(ExperimentalWarningHandler.confirmLabelFor(new BackupConfirmScreen(
                        () -> {
                        },
                        (backup, eraseCache) -> {
                        },
                        Component.translatable("selectWorld.backupQuestion.customized"),
                        Component.literal(MESSAGE_TEXT),
                        false)),
                "a customized-backup question is not an experimental gate and must pass through");
    }

    @Test
    void leavesPlainScreensAlone() {
        assertNull(ExperimentalWarningHandler.confirmLabelFor(
                        new Screen(Component.literal("plain")) {
                        }),
                "screens without any warning must pass through untouched");
    }
}
