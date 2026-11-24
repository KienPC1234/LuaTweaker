package com.luatweaker.common.container;

import com.luatweaker.api.content.MachineLabelSpec;
import com.luatweaker.common.client.luamods.LuamodLayouts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Draws machine text labels inside {@link CustomContainerScreen}: alignment,
 * scale, shadow, multi-line splitting, {@code {Key}} placeholders, and strict
 * pixel-width clamping so labels never bleed past the beveled container frame.
 */
final class ContainerLabelRenderer {

    private static final String ALIGN_CENTER = "center";
    private static final String ALIGN_RIGHT = "right";
    private static final float MIN_SCALE = 0.25f;
    private static final float MAX_SCALE = 3.0f;

    private ContainerLabelRenderer() {
    }

    static void renderLabels(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu,
                             List<MachineLabelSpec> labels) {
        int panelH = ContainerLayout.imageHeight(menu.getContainerRows());
        for (MachineLabelSpec label : labels) {
            renderLabel(guiGraphics, font, menu, label, panelH);
        }
    }

    private static void renderLabel(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu,
                                    MachineLabelSpec label, int panelH) {
        String resolved = GuiAnchor.placeholders(label.textOrVar(), key -> labelValue(menu, key));
        List<String> lines = GuiAnchor.lines(resolved);
        float scale = Math.clamp(label.scale(), MIN_SCALE, MAX_SCALE);
        int advance = Math.max(1, Math.round(font.lineHeight * scale));
        int availScaledPixels = ContainerBounds.maxLabelWidth(
                label.align(), label.x(), ContainerLayout.PANEL_WIDTH);
        int maxUnscaledWidth = (int) Math.floor(availScaledPixels / scale);
        if (maxUnscaledWidth <= 0) {
            return;
        }

        for (int line = 0; line < lines.size(); line++) {
            int drawY = label.y() + line * advance;
            if (drawY < ContainerBounds.INNER_INSET || drawY + advance > panelH - ContainerBounds.INNER_INSET) {
                continue;
            }
            String text = LuamodLayouts.trim(font, lines.get(line), maxUnscaledWidth);
            if (text.isEmpty()) {
                continue;
            }
            drawSingleLine(guiGraphics, font, label, text, drawY, scale);
        }
    }

    private static void drawSingleLine(GuiGraphics guiGraphics, Font font, MachineLabelSpec label,
                                       String text, int drawY, float scale) {
        guiGraphics.pose().pushPose();
        if (ALIGN_CENTER.equalsIgnoreCase(label.align())) {
            float centerX = Math.clamp(ContainerLayout.PANEL_WIDTH / 2.0f + label.x(),
                    ContainerBounds.INNER_INSET, ContainerLayout.PANEL_WIDTH - ContainerBounds.INNER_INSET);
            guiGraphics.pose().translate(centerX, drawY, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            if (label.shadow()) {
                guiGraphics.drawCenteredString(font, text, 1, 1, shadowOf(label.color()));
            }
            guiGraphics.drawCenteredString(font, text, 0, 0, label.color());
        } else {
            int scaledTextW = Math.round(font.width(text) * scale);
            int rawX = ALIGN_RIGHT.equalsIgnoreCase(label.align())
                    ? ContainerLayout.PANEL_WIDTH - Math.max(ContainerBounds.INNER_INSET, label.x()) - scaledTextW
                    : Math.max(ContainerBounds.INNER_INSET, label.x());
            int drawX = ContainerBounds.clampX(rawX, scaledTextW, ContainerLayout.PANEL_WIDTH);
            guiGraphics.pose().translate(drawX, drawY, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.drawString(font, text, 0, 0, label.color(), label.shadow());
        }
        guiGraphics.pose().popPose();
    }

    private static String labelValue(CustomContainerMenu menu, String key) {
        Integer synced = menu.getCustomValueOrNull(key);
        if (synced != null) {
            return String.valueOf(synced);
        }
        return switch (key) {
            case "Energy" -> String.valueOf(menu.getEnergyStored());
            case "MaxEnergy" -> String.valueOf(menu.getEnergyCapacity());
            case "Fluid" -> String.valueOf(menu.getFluidAmount());
            case "MaxFluid" -> String.valueOf(menu.getFluidCapacity());
            case "Progress" -> String.valueOf(Math.round(menu.getProgress() * 100));
            default -> null;
        };
    }

    private static int shadowOf(int argb) {
        return (argb & 0xFCFCFC) >> 2 | argb & 0xFF000000;
    }
}
