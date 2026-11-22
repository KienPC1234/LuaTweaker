package com.luatweaker.common.container;

import com.luatweaker.api.content.MachineButtonSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client screen for Lua-configured container blocks. The panel height follows the
 * container's row count (vanilla chest formula, {@link ContainerLayout}); the slot
 * cells are drawn on top at each slot's actual position (custom slot positions and
 * per-slot textures from the Lua builder are honored), and locked slots get a dark
 * overlay so players instantly see they cannot be used.
 */
public class CustomContainerScreen extends AbstractContainerScreen<CustomContainerMenu> {
    private static final ResourceLocation VANILLA_CONTAINER_BG =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");
    private static final int TOP_STRIP = 17;
    private static final int BOTTOM_INVENTORY_HEIGHT = 96;
    private static final int TEXTURE_SHEET_SIZE = 256;
    private static final int LABEL_COLOR = 0x404040;
    private static final int VANILLA_BG_COLOR = 0xFFC6C6C6;
    private static final int BORDER_HIGHLIGHT = 0xFFFFFFFF;
    private static final int BORDER_SHADOW = 0xFF373737;
    private static final int SLOT_CELL_INSET = 1;
    private static final int LOCKED_SLOT_OVERLAY = 0x7F1C1C1C;
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
        int originX = (width - imageWidth) / 2;
        int originY = (height - imageHeight) / 2;
        for (MachineButtonSpec spec : buttons) {
            addRenderableWidget(createButton(spec, originX, originY));
        }
    }

    private Button createButton(MachineButtonSpec spec, int originX, int originY) {
        int buttonX = originX + GuiAnchor.resolveX(spec.anchor(), imageWidth, spec.width(), spec.x());
        int buttonY = originY + GuiAnchor.resolveY(spec.anchor(), imageHeight, spec.height(), spec.y());
        return Button.builder(Component.literal(spec.label()), clicked -> {
                    if (menu.getBlockPos() != null) {
                        dev.architectury.networking.NetworkManager.sendToServer(
                                new com.luatweaker.common.network.ContainerButtonPayload(
                                        menu.getBlockPos(), spec.id()));
                    }
                })
                .bounds(buttonX, buttonY, spec.width(), spec.height())
                .build();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int originX = (width - imageWidth) / 2;
        int originY = (height - imageHeight) / 2;
        renderPanel(guiGraphics, originX, originY);
        renderSlotCells(guiGraphics, originX, originY);
        if (menu.getType() != null) {
            List<com.luatweaker.api.content.MachineBarSpec> bars =
                    CustomContainerRegistry.CONTAINER_BARS.get(menu.getType());
            if (bars != null) {
                ContainerScreenDecor.renderBars(guiGraphics, menu, bars, originX, originY);
            }
        }
    }

    private void renderPanel(GuiGraphics guiGraphics, int originX, int originY) {
        ResourceLocation custom = menu.getType() != null
                ? customTexture(CustomContainerRegistry.CONTAINER_TEXTURES.get(menu.getType()))
                : null;
        if (custom != null) {
            guiGraphics.blit(custom, originX, originY, 0, 0, imageWidth, imageHeight,
                    TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
            return;
        }
        int topH = menu.getContainerRows() * 18 + TOP_STRIP;
        if (menu.isManualSlots()) {
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX, originY, 0, 0, imageWidth, TOP_STRIP);
            guiGraphics.fill(originX + 1, originY + TOP_STRIP, originX + imageWidth - 1, originY + topH, VANILLA_BG_COLOR);
            guiGraphics.fill(originX, originY + TOP_STRIP, originX + 1, originY + topH, BORDER_HIGHLIGHT);
            guiGraphics.fill(originX + imageWidth - 1, originY + TOP_STRIP, originX + imageWidth, originY + topH, BORDER_SHADOW);
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX, originY + topH, 0, 126, imageWidth, BOTTOM_INVENTORY_HEIGHT);
        } else {
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX, originY, 0, 0, imageWidth, topH);
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX, originY + topH, 0, 126, imageWidth, BOTTOM_INVENTORY_HEIGHT);
        }
    }

    @Nullable
    private static ResourceLocation customTexture(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    private void renderSlotCells(GuiGraphics guiGraphics, int originX, int originY) {
        int cellSize = ContainerLayout.CELL;
        boolean hasCustomSlot = menu.getType() != null
                && CustomContainerRegistry.CONTAINER_SLOT_TEXTURES.containsKey(menu.getType());
        ResourceLocation customSlotRl = hasCustomSlot
                ? customTexture(CustomContainerRegistry.CONTAINER_SLOT_TEXTURES.get(menu.getType()))
                : null;

        for (Slot slot : menu.slots) {
            if (!java.util.Objects.equals(slot.container, menu.getContainer())) {
                continue;
            }
            int slotX = originX + slot.x - SLOT_CELL_INSET;
            int slotY = originY + slot.y - SLOT_CELL_INSET;

            if (customSlotRl != null) {
                guiGraphics.blit(customSlotRl, slotX, slotY, 0, 0, cellSize, cellSize, cellSize, cellSize);
            } else if (menu.isManualSlots()) {
                guiGraphics.blitSprite(SLOT_SPRITE, slotX, slotY, cellSize, cellSize);
            }

            if (slot instanceof ContainerFilteredSlot filtered && filtered.isLocked()) {
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, LOCKED_SLOT_OVERLAY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        guiGraphics.drawString(this.font, PLAYER_INV_LABEL, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);
        ContainerScreenDecor.renderLabels(guiGraphics, this.font, menu);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        if (menu.getType() == null) {
            return;
        }
        List<com.luatweaker.api.content.MachineBarSpec> bars =
                CustomContainerRegistry.CONTAINER_BARS.get(menu.getType());
        if (bars == null) {
            return;
        }
        ContainerBarTooltip.render(guiGraphics, this.font, menu, bars, mouseX, mouseY,
                (width - imageWidth) / 2, (height - imageHeight) / 2);
    }
}

