package com.luatweaker.platform.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * Client screen for Lua-configured container blocks. The panel height follows the
 * container's row count (vanilla chest formula, {@link ContainerLayout}); the slot
 * cells are drawn on top at each slot's actual position (custom slot positions and
 * per-slot textures from the Lua builder are honored), and locked slots get a dark
 * overlay so players instantly see they cannot be used.
 */
public class CustomContainerScreen extends AbstractContainerScreen<CustomContainerMenu> {

    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("luatweaker", "textures/gui/crate_panel.png");
    private static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("luatweaker", "textures/gui/slot.png");
    /** Height of the 256x256 panel sheet below the top border (frame bottom strip). */
    private static final int PANEL_SHEET_HEIGHT = 222;
    private static final int TOP_STRIP = 17;
    private static final int BOTTOM_STRIP = 9;
    /** Semi-transparent dark background painted under every GUI bar. */
    private static final int BAR_BACKGROUND = 0x7F101010;
    /** Default ARGB fill colors per bar source when the Lua config passes 0. */
    private static final int DEFAULT_ENERGY_COLOR = 0xFF00E676;
    private static final int DEFAULT_FLUID_COLOR = 0xFF2196F3;
    private static final int DEFAULT_PROGRESS_COLOR = 0xFFFFC107;
    /** Overlay color for read-only (locked) slots. */
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        renderPanel(guiGraphics, x, y);
        renderSlotCells(guiGraphics, x, y);
        renderBars(guiGraphics, x, y);
    }

    private void renderPanel(GuiGraphics guiGraphics, int x, int y) {
        ResourceLocation panel = PANEL_TEXTURE;
        if (menu.getType() != null) {
            String custom = CustomContainerRegistry.CONTAINER_TEXTURES.get(menu.getType());
            if (custom != null && !custom.isBlank()) {
                ResourceLocation parsed = ResourceLocation.tryParse(custom);
                if (parsed != null) {
                    // A custom texture is a full panel for the configured row count
                    // (176 x imageHeight region from its top-left corner).
                    guiGraphics.blit(parsed, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
                    return;
                }
            }
        }
        // Default crate frame: fixed top/bottom strips + stretchable side borders,
        // so any row count (1..6) renders with a complete frame.
        int middle = imageHeight - TOP_STRIP - BOTTOM_STRIP;
        guiGraphics.blit(PANEL_TEXTURE, x, y, 0, 0, imageWidth, TOP_STRIP, 256, 256);
        guiGraphics.blit(PANEL_TEXTURE, x, y + TOP_STRIP, 0, TOP_STRIP, 1, middle, 256, 256);
        guiGraphics.blit(PANEL_TEXTURE, x + imageWidth - 1, y + TOP_STRIP, imageWidth - 1, TOP_STRIP, 1, middle, 256, 256);
        guiGraphics.blit(PANEL_TEXTURE, x, y + imageHeight - BOTTOM_STRIP, 0, PANEL_SHEET_HEIGHT - BOTTOM_STRIP, imageWidth, BOTTOM_STRIP, 256, 256);
    }

    private void renderSlotCells(GuiGraphics guiGraphics, int x, int y) {
        ResourceLocation cell = resolveSlotTexture();
        int cellSize = ContainerLayout.CELL;
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            guiGraphics.blit(cell, sx, sy, 0, 0, cellSize, cellSize, cellSize, cellSize);
            if (slot instanceof ContainerFilteredSlot filtered && filtered.isLocked()) {
                guiGraphics.fill(sx, sy, sx + cellSize, sy + cellSize, LOCKED_SLOT_OVERLAY);
            }
        }
    }

    private ResourceLocation resolveSlotTexture() {
        if (menu.getType() != null) {
            String custom = CustomContainerRegistry.CONTAINER_SLOT_TEXTURES.get(menu.getType());
            if (custom != null && !custom.isBlank()) {
                ResourceLocation parsed = ResourceLocation.tryParse(custom);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return SLOT_TEXTURE;
    }

    private void renderBars(GuiGraphics guiGraphics, int x, int y) {
        if (menu.getType() == null) return;
        java.util.List<com.luatweaker.api.content.MachineBarSpec> bars =
                CustomContainerRegistry.CONTAINER_BARS.get(menu.getType());
        if (bars == null) return;
        for (com.luatweaker.api.content.MachineBarSpec bar : bars) {
            int bx = x + bar.x();
            int by = y + bar.y();
            float ratio = barRatio(bar.source());
            int color = bar.color() > 0 ? bar.color() : defaultBarColor(bar.source());
            guiGraphics.fill(bx, by, bx + bar.width(), by + bar.height(), BAR_BACKGROUND);
            if (ratio > 0f) {
                if (bar.width() >= bar.height()) {
                    int fillWidth = Math.round(bar.width() * ratio);
                    guiGraphics.fill(bx, by, bx + fillWidth, by + bar.height(), color);
                } else {
                    int fillHeight = Math.round(bar.height() * ratio);
                    guiGraphics.fill(bx, by + bar.height() - fillHeight, bx + bar.width(), by + bar.height(), color);
                }
            }
        }
    }

    private float barRatio(String source) {
        if (source == null) return 0f;
        return switch (source) {
            case "energy" -> menu.getEnergyCapacity() > 0
                    ? Math.min(1f, (float) menu.getEnergyStored() / menu.getEnergyCapacity()) : 0f;
            case "fluid" -> menu.getFluidCapacity() > 0
                    ? Math.min(1f, (float) menu.getFluidAmount() / menu.getFluidCapacity()) : 0f;
            case "progress" -> Math.min(1f, Math.max(0f, menu.getProgress()));
            default -> 0f;
        };
    }

    private int defaultBarColor(String source) {
        if (source == null) return DEFAULT_ENERGY_COLOR;
        return switch (source) {
            case "energy" -> DEFAULT_ENERGY_COLOR;
            case "fluid" -> DEFAULT_FLUID_COLOR;
            case "progress" -> DEFAULT_PROGRESS_COLOR;
            default -> DEFAULT_ENERGY_COLOR;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, PLAYER_INV_LABEL, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
