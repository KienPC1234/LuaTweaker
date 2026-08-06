package com.luatweaker.platform.client;

import com.luatweaker.platform.config.LuaTweakerConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Auto-accepts Minecraft's "Warning! These settings are using experimental
 * features" screen when the mod config option {@code suppressExperimentalWarning}
 * is enabled (default: true), because the LuaTweaker virtual datapack is
 * always loaded into every new world.
 *
 * <p>Implemented by pressing the "Yes" button once the confirm screen has
 * initialized - the same action a player clicking "Yes" would perform, so
 * world creation proceeds normally.</p>
 */
public class ExperimentalWarningHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ConfirmExperimentalFeaturesScreen screen)) return;
        if (!LuaTweakerConfig.SUPPRESS_EXPERIMENTAL_WARNING.get()) return;

        Component yesText = Component.translatable("gui.yes");
        for (var widget : screen.children()) {
            if (widget instanceof Button button && yesText.getString().equals(button.getMessage().getString())) {
                button.onPress();
                return;
            }
        }
    }
}
