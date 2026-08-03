package com.luatweaker.platform.crate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Client screen for Lua-configured crate blocks. The panel texture is a plain
 * 176x190 frame with the vanilla-style player inventory; the crate's own slot
 * cells are drawn on top according to the menu's rows x cols grid, so any
 * layout configured from Lua renders correctly.
 */
public class ContainerCrateScreen extends AbstractContainerScreen<ContainerCrateMenu> {

    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("luatweaker", "textures/gui/crate_panel.png");
    private static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("luatweaker", "textures/gui/slot.png");
    private static final Component PLAYER_INV_LABEL = Component.translatable("container.inventory");

    public ContainerCrateScreen(ContainerCrateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        ResourceLocation panel = PANEL_TEXTURE;
        if (menu.getType() != null) {
            String custom = ContainerCrateRegistry.CRATE_TEXTURES.get(menu.getType());
            if (custom != null && !custom.isBlank()) {
                ResourceLocation parsed = ResourceLocation.tryParse(custom);
                if (parsed != null) panel = parsed;
            }
        }
        guiGraphics.blit(panel, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        int rows = menu.getCrateRows();
        int cols = menu.getCrateCols();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int sx = x + 34 + col * 18;
                int sy = y + 17 + row * 18;
                guiGraphics.blit(SLOT_TEXTURE, sx, sy, 0, 0, 16, 16, 16, 16);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0x404040, false);
        guiGraphics.drawString(this.font, PLAYER_INV_LABEL, 8, 94, 0x404040, false);
    }
}
