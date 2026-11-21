package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.config.LuaTweakerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Auto-accepts Minecraft's "experimental features" confirmations when the mod config option
 * {@code suppressExperimentalWarning} is enabled (default: true), because the LuaTweaker virtual
 * datapack is loaded into every world and can make it experimental.
 *
 * <p>Minecraft 1.21.1 uses three different screens for this warning:</p>
 * <ul>
 *   <li>{@link ConfirmExperimentalFeaturesScreen} while creating a world — confirm button is
 *       {@code gui.proceed}, not {@code gui.yes};</li>
 *   <li>a plain {@link ConfirmScreen} titled {@code selectWorld.warning.experimental.title}
 *       right after the world is created — confirm button is {@code gui.yes};</li>
 *   <li>a {@link BackupConfirmScreen} titled {@code selectWorld.backupQuestion.experimental}
 *       when opening an older experimental world — the safe "create backup and load" button is
 *       pressed, so suppressing the prompt never risks the world.</li>
 * </ul>
 *
 * <p>The press is deferred to the next client tick: these screens are created from a button
 * callback, and their own callback creates or opens the world (another {@code setScreen}), so
 * pressing during {@code Screen.init} would re-enter the screen lifecycle.</p>
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

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!LuaTweakerConfig.SUPPRESS_EXPERIMENTAL_WARNING.get()) { return; }

        Screen screen = event.getScreen();
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

    /**
     * Picks the confirm button for a known experimental-features screen, or {@code null} when the
     * screen is unrelated (including customized-world backups, which must keep their prompt).
     */
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

    /** Presses the first button whose label matches; returns false when no button matches. */
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
