package com.luatweaker.common.container;

import com.luatweaker.api.content.MachineLabelSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Draws machine text labels: alignment, scale, shadow, multi-line and
 * {Key} placeholders resolved from synced block-entity values.
 *
 * <p>Split from {@link ContainerScreenDecor} so each renderer owns one widget
 * family: labels measure text at render time, bars and slots do not.</p>
 */
final class ContainerLabelRenderer {

    private static final String ALIGN_CENTER = "center";
    private static final String ALIGN_RIGHT = "right";

    private ContainerLabelRenderer() {
    }

    static void renderLabels(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu,
            List<MachineLabelSpec> labels) {
        for (MachineLabelSpec label : labels) {
            renderLabel(guiGraphics, font, menu, label);
        }
    }

    private static void renderLabel(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu,
                                    MachineLabelSpec label) {
        String resolved = GuiAnchor.placeholders(label.textOrVar(), key -> labelValue(menu, key));
        List<String> lines = GuiAnchor.lines(resolved);
        float scale = label.scale();
        int advance = Math.round(font.lineHeight * scale);
        for (int line = 0; line < lines.size(); line++) {
            String text = lines.get(line);
            int drawY = label.y() + line * advance;
            guiGraphics.pose().pushPose();
            if (ALIGN_CENTER.equalsIgnoreCase(label.align())) {
                guiGraphics.pose().translate(ContainerLayout.PANEL_WIDTH / 2.0f + label.x(), drawY, 0);
                guiGraphics.pose().scale(scale, scale, 1.0f);
                if (label.shadow()) {
                    guiGraphics.drawCenteredString(font, text, 1, 1, shadowOf(label.color()));
                }
                guiGraphics.drawCenteredString(font, text, 0, 0, label.color());
            } else {
                int drawX = ALIGN_RIGHT.equalsIgnoreCase(label.align())
                        ? GuiAnchor.labelX(label.align(), ContainerLayout.PANEL_WIDTH,
                                font.width(text), label.x())
                        : label.x();
                guiGraphics.pose().translate(drawX, drawY, 0);
                guiGraphics.pose().scale(scale, scale, 1.0f);
                guiGraphics.drawString(font, text, 0, 0, label.color(), label.shadow());
            }
            guiGraphics.pose().popPose();
        }
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

    /** Vanilla-style drop shadow: the color darkened toward black, alpha kept. */
    private static int shadowOf(int argb) {
        return (argb & 0xFCFCFC) >> 2 | argb & 0xFF000000;
    }
}
