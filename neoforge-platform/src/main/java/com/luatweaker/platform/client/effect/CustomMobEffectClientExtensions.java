package com.luatweaker.platform.client.effect;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import org.jetbrains.annotations.NotNull;

/**
 * Renders custom texture icons for registered MobEffects in both HUD and inventory screens.
 */
public final class CustomMobEffectClientExtensions implements IClientMobEffectExtensions {

    private static final int ICON_SIZE = 18;
    private final ResourceLocation textureLocation;

    public CustomMobEffectClientExtensions(@NotNull ResourceLocation textureLocation) {
        this.textureLocation = textureLocation;
    }

    @Override
    public boolean renderInventoryIcon(MobEffectInstance instance,
                                       EffectRenderingInventoryScreen<?> screen,
                                       GuiGraphics guiGraphics,
                                       int x, int y, int blitOffset) {
        guiGraphics.blit(textureLocation, x, y, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        return true;
    }

    @Override
    public boolean renderGuiIcon(MobEffectInstance instance,
                                 Gui gui,
                                 GuiGraphics guiGraphics,
                                 int x, int y, float z, float alpha) {
        guiGraphics.blit(textureLocation, x, y, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        return true;
    }
}
