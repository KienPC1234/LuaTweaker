package com.luatweaker.common.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.config.LuaTweakerConfig;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Auto-accepts Minecraft's "experimental features" confirmations when the mod config option
 * {@code suppressExperimentalWarning} is enabled (default: true).
 */
public final class ExperimentalWarningHandler {

    private ExperimentalWarningHandler() {
    }

    private static final Component WORLD_WARNING_TITLE =
            Component.translatable("selectWorld.warning.experimental.title");
    private static final Component BACKUP_EXPERIMENTAL_TITLE =
            Component.translatable("selectWorld.backupQuestion.experimental");
    private static final Component BACKUP_AND_LOAD =
            Component.translatable("selectWorld.backupJoinConfirmButton");

    public static void register() {
        ClientGuiEvent.INIT_POST.register(ExperimentalWarningHandler::onScreenInit);
    }

    public static void onScreenInit(Screen screen, ScreenAccess access) {
        if (!LuaTweakerConfig.isSuppressExperimentalWarning()) { return; }

        Component confirmLabel = confirmLabelFor(screen);
        if (confirmLabel == null) { return; }

        Minecraft.getInstance().execute(() -> {
            if (!Objects.equals(Minecraft.getInstance().screen, screen)) { return; }
            if (!pressButton(screen, confirmLabel)) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[LuaTweaker] Could not auto-confirm " + screen.getClass().getSimpleName()
                                + ": no button labelled '" + confirmLabel.getString() + "'");
            }
        });
    }

    static @Nullable Component confirmLabelFor(@NotNull Screen screen) {
        if (screen instanceof ConfirmExperimentalFeaturesScreen) {
            return CommonComponents.GUI_PROCEED;
        }
        if (screen instanceof ConfirmScreen && WORLD_WARNING_TITLE.equals(screen.getTitle())) {
            return CommonComponents.GUI_YES;
        }
        if (screen instanceof BackupConfirmScreen && BACKUP_EXPERIMENTAL_TITLE.equals(screen.getTitle())) {
            return BACKUP_AND_LOAD;
        }
        return null;
    }

    static boolean pressButton(@NotNull Screen screen, @NotNull Component label) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && label.equals(button.getMessage())) {
                button.onPress();
                return true;
            }
        }
        return false;
    }
}
