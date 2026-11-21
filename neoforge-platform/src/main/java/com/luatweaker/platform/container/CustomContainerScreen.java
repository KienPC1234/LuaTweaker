package com.luatweaker.platform.container;

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
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("luatweaker", "textures/gui/crate_panel.png");
    private static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("luatweaker", "textures/gui/slot.png");
    /** Height of the 256x256 panel sheet below the top border (frame bottom strip).
     * Intentionally fixed engine default: matches the bundled crate_panel.png asset. */
    private static final int PANEL_SHEET_HEIGHT = 222;
    /** Fixed top/bottom frame strips of the bundled panel asset (not per-mod config). */
    private static final int TOP_STRIP = 17;
    private static final int BOTTOM_STRIP = 9;
    /** Vanilla GUI texture sheet size (every blit() source region is 256x256). */
    private static final int TEXTURE_SHEET_SIZE = 256;
    /** Vanilla inventory label color (ChestScreen convention, not tunable). */
    private static final int LABEL_COLOR = 0x404040;
    /** Slot cell texture overhang outside the slot bounds (vanilla 1px border overlap). */
    private static final int SLOT_CELL_INSET = 1;
    /** Overlay color for read-only (locked) slots (engine-fixed, no per-mod config path). */
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
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.luatweaker.platform.network.ContainerButtonPayload(
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
            // A custom texture is a full panel for the configured row count
            // (176 x imageHeight region from its top-left corner).
            guiGraphics.blit(custom, originX, originY, 0, 0, imageWidth, imageHeight,
                    TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
            return;
        }
        // Default crate frame: fixed top/bottom strips + stretchable side borders,
        // so any row count (1..6) renders with a complete frame.
        int middle = imageHeight - TOP_STRIP - BOTTOM_STRIP;
        guiGraphics.blit(PANEL_TEXTURE, originX, originY, 0, 0, imageWidth, TOP_STRIP,
                TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
        guiGraphics.blit(PANEL_TEXTURE, originX, originY + TOP_STRIP, 0, TOP_STRIP, 1, middle,
                TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
        guiGraphics.blit(PANEL_TEXTURE, originX + imageWidth - 1, originY + TOP_STRIP,
                imageWidth - 1, TOP_STRIP, 1, middle, TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
        guiGraphics.blit(PANEL_TEXTURE, originX, originY + imageHeight - BOTTOM_STRIP,
                0, PANEL_SHEET_HEIGHT - BOTTOM_STRIP, imageWidth, BOTTOM_STRIP,
                TEXTURE_SHEET_SIZE, TEXTURE_SHEET_SIZE);
    }

    @Nullable
    private static ResourceLocation customTexture(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    private void renderSlotCells(GuiGraphics guiGraphics, int originX, int originY) {
        ResourceLocation cell = resolveSlotTexture();
        int cellSize = ContainerLayout.CELL;
        for (Slot slot : menu.slots) {
            int slotX = originX + slot.x - SLOT_CELL_INSET;
            int slotY = originY + slot.y - SLOT_CELL_INSET;
            guiGraphics.blit(cell, slotX, slotY, 0, 0, cellSize, cellSize, cellSize, cellSize);
            if (slot instanceof ContainerFilteredSlot filtered && filtered.isLocked()) {
                guiGraphics.fill(slotX, slotY, slotX + cellSize, slotY + cellSize, LOCKED_SLOT_OVERLAY);
            }
        }
    }

    private ResourceLocation resolveSlotTexture() {
        ResourceLocation custom = menu.getType() != null
                ? customTexture(CustomContainerRegistry.CONTAINER_SLOT_TEXTURES.get(menu.getType()))
                : null;
        return custom != null ? custom : SLOT_TEXTURE;
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

