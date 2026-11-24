package com.luatweaker.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 9-slice panel frame and slot-cell renderer for {@link CustomContainerScreen}.
 */
final class ContainerPanelRenderer {
    private static final ResourceLocation VANILLA_CONTAINER_BG =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");
    private static final int TOP_STRIP = 17;
    private static final int SIDE_BORDER_WIDTH = 7;
    private static final int MAX_VANILLA_SLICE_ROWS = 6;
    private static final int BOTTOM_INVENTORY_UV_Y = 126;
    private static final int BOTTOM_INVENTORY_HEIGHT = 96;
    private static final int TEXTURE_SHEET_SIZE = 256;
    private static final int VANILLA_BG_COLOR = 0xFFC6C6C6;
    private static final int SLOT_CELL_INSET = 1;
    private static final int LOCKED_SLOT_OVERLAY = 0x7F1C1C1C;

    private ContainerPanelRenderer() {
    }

    static void renderPanel(GuiGraphics guiGraphics, CustomContainerMenu menu,
                            int originX, int originY, int imageWidth, int imageHeight) {
        ResourceLocation custom = menu.getType() != null
                ? customTexture(CustomContainerRegistry.CONTAINER_TEXTURES.get(menu.getType()))
                : null;
        if (custom != null) {
            guiGraphics.blit(custom, originX, originY, 0, 0, imageWidth, imageHeight,
                    TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
            return;
        }
        guiGraphics.blit(VANILLA_CONTAINER_BG, originX, originY, 0, 0, imageWidth, TOP_STRIP);
        int rows = Math.max(1, menu.getContainerRows());
        if (menu.isManualSlots()) {
            renderManualBody(guiGraphics, originX, originY + TOP_STRIP, imageWidth, rows);
        } else {
            renderGridBody(guiGraphics, originX, originY + TOP_STRIP, imageWidth, rows);
        }
        int topH = rows * ContainerLayout.CELL + TOP_STRIP;
        guiGraphics.blit(VANILLA_CONTAINER_BG, originX, originY + topH,
                0, BOTTOM_INVENTORY_UV_Y, imageWidth, BOTTOM_INVENTORY_HEIGHT);
    }

    private static void renderManualBody(GuiGraphics guiGraphics, int originX, int bodyY, int imageWidth, int rows) {
        int bodyHeight = rows * ContainerLayout.CELL;
        guiGraphics.fill(originX + SIDE_BORDER_WIDTH, bodyY,
                originX + imageWidth - SIDE_BORDER_WIDTH, bodyY + bodyHeight, VANILLA_BG_COLOR);
        int remaining = rows;
        int cursorY = bodyY;
        while (remaining > 0) {
            int sliceRows = Math.min(MAX_VANILLA_SLICE_ROWS, remaining);
            int sliceH = sliceRows * ContainerLayout.CELL;
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX, cursorY,
                    0, TOP_STRIP, SIDE_BORDER_WIDTH, sliceH);
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX + imageWidth - SIDE_BORDER_WIDTH, cursorY,
                    imageWidth - SIDE_BORDER_WIDTH, TOP_STRIP, SIDE_BORDER_WIDTH, sliceH);
            cursorY += sliceH;
            remaining -= sliceRows;
        }
    }

    private static void renderGridBody(GuiGraphics guiGraphics, int originX, int bodyY, int imageWidth, int rows) {
        int remaining = rows;
        int cursorY = bodyY;
        while (remaining > 0) {
            int sliceRows = Math.min(MAX_VANILLA_SLICE_ROWS, remaining);
            int sliceH = sliceRows * ContainerLayout.CELL;
            guiGraphics.blit(VANILLA_CONTAINER_BG, originX, cursorY, 0, TOP_STRIP, imageWidth, sliceH);
            cursorY += sliceH;
            remaining -= sliceRows;
        }
    }

    static void renderSlotCells(GuiGraphics guiGraphics, CustomContainerMenu menu, int originX, int originY) {
        int cellSize = ContainerLayout.CELL;
        ResourceLocation customSlotRl = menu.getType() != null
                ? customTexture(CustomContainerRegistry.CONTAINER_SLOT_TEXTURES.get(menu.getType()))
                : null;

        for (Slot slot : menu.slots) {
            if (!Objects.equals(slot.container, menu.getContainer())) {
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

    @Nullable
    private static ResourceLocation customTexture(@Nullable String raw) {
        return raw == null || raw.isBlank() ? null : ResourceLocation.tryParse(raw);
    }
}
