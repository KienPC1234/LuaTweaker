package com.luatweaker.common.container;

import com.luatweaker.api.content.MachineBarSpec;
import com.luatweaker.api.content.MachineButtonSpec;
import com.luatweaker.common.client.luamods.LuamodLayouts;
import com.luatweaker.common.network.ContainerButtonPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Client screen for Lua-configured container blocks.
 * Renders authentic 9-slice vanilla beveled borders for arbitrary row counts,
 * clamps widgets/labels strictly inside the panel frame, and leaves full-screen
 * background dimming, dragged items, and tooltips unclipped.
 */
public class CustomContainerScreen extends AbstractContainerScreen<CustomContainerMenu> {
    private static final int LABEL_COLOR = 0x404040;
    private static final int INNER_CLIP_INSET = 4;
    private static final Component PLAYER_INV_LABEL = Component.translatable("container.inventory");

    public CustomContainerScreen(CustomContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = ContainerLayout.PANEL_WIDTH;
        this.imageHeight = ContainerLayout.imageHeight(menu.getContainerRows());
        this.inventoryLabelX = ContainerLayout.LEFT_MARGIN;
        this.inventoryLabelY = ContainerLayout.inventoryLabelY(menu.getContainerRows());
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = Math.max(2, (this.height - this.imageHeight) / 2);
        addButtons();
    }

    private void addButtons() {
        if (menu.getType() == null) {
            return;
        }
        List<MachineButtonSpec> buttons = CustomContainerRegistry.CONTAINER_BUTTONS.get(menu.getType());
        if (buttons == null) {
            return;
        }
        for (MachineButtonSpec spec : buttons) {
            addRenderableWidget(createButton(spec));
        }
    }

    private Button createButton(MachineButtonSpec spec) {
        int btnW = ContainerBounds.clampWidth(spec.width(), imageWidth);
        int btnH = ContainerBounds.clampHeight(spec.height(), imageHeight);
        int relX = ContainerBounds.clampX(
                GuiAnchor.resolveX(spec.anchor(), imageWidth, btnW, spec.x()), btnW, imageWidth);
        int relY = ContainerBounds.clampY(
                GuiAnchor.resolveY(spec.anchor(), imageHeight, btnH, spec.y()), btnH, imageHeight);
        String trimmedLabel = LuamodLayouts.trim(this.font, spec.label(), Math.max(8, btnW - 8));
        return Button.builder(Component.literal(trimmedLabel), clicked -> {
                    if (menu.getBlockPos() != null) {
                        NetworkManager.sendToServer(
                                new ContainerButtonPayload(menu.getBlockPos(), spec.id()));
                    }
                })
                .bounds(this.leftPos + relX, this.topPos + relY, btnW, btnH)
                .build();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        ContainerPanelRenderer.renderPanel(guiGraphics, menu, this.leftPos, this.topPos,
                this.imageWidth, this.imageHeight);
        guiGraphics.enableScissor(
                this.leftPos + INNER_CLIP_INSET,
                this.topPos + INNER_CLIP_INSET,
                this.leftPos + this.imageWidth - INNER_CLIP_INSET,
                this.topPos + this.imageHeight - INNER_CLIP_INSET);
        try {
            ContainerPanelRenderer.renderSlotCells(guiGraphics, menu, this.leftPos, this.topPos);
            if (menu.getType() != null) {
                List<MachineBarSpec> bars = CustomContainerRegistry.CONTAINER_BARS.get(menu.getType());
                if (bars != null) {
                    ContainerScreenDecor.renderBars(guiGraphics, menu, bars, this.leftPos, this.topPos);
                }
            }
        } finally {
            guiGraphics.disableScissor();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int maxTitleW = ContainerBounds.maxLabelWidth(GuiAnchor.ALIGN_LEFT, this.titleLabelX, this.imageWidth);
        int maxInvW = ContainerBounds.maxLabelWidth(GuiAnchor.ALIGN_LEFT, this.inventoryLabelX, this.imageWidth);
        guiGraphics.drawString(this.font,
                LuamodLayouts.trim(this.font, this.title.getString(), maxTitleW),
                this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        guiGraphics.drawString(this.font,
                LuamodLayouts.trim(this.font, PLAYER_INV_LABEL.getString(), maxInvW),
                this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);

        guiGraphics.enableScissor(
                this.leftPos + INNER_CLIP_INSET,
                this.topPos + INNER_CLIP_INSET,
                this.leftPos + this.imageWidth - INNER_CLIP_INSET,
                this.topPos + this.imageHeight - INNER_CLIP_INSET);
        try {
            ContainerScreenDecor.renderLabels(guiGraphics, this.font, menu);
        } finally {
            guiGraphics.disableScissor();
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        if (menu.getType() == null) {
            return;
        }
        List<MachineBarSpec> bars = CustomContainerRegistry.CONTAINER_BARS.get(menu.getType());
        if (bars != null) {
            ContainerBarTooltip.render(guiGraphics, this.font, menu, bars, mouseX, mouseY,
                    this.leftPos, this.topPos);
        }
    }
}
