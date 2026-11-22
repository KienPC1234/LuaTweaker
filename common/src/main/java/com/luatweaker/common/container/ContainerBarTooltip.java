package com.luatweaker.common.container;

import com.luatweaker.api.content.MachineBarSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Hover tooltips for machine GUI bars.
 *
 * <p>Split from {@link ContainerScreenDecor}: filling bars and formatting their
 * tooltips are separate jobs. Hit-testing uses the same anchor resolution as
 * the fill pass, so tooltips track anchored bars instead of their raw offsets.</p>
 */
final class ContainerBarTooltip {

    private static final int PERCENT = 100;

    private ContainerBarTooltip() {
    }

    /** Renders the tooltip of the bar under the cursor; returns true when one was drawn. */
    static boolean render(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu,
                          List<MachineBarSpec> bars, int mouseX, int mouseY,
                          int originX, int originY) {
        int panelH = ContainerLayout.imageHeight(menu.getContainerRows());
        for (MachineBarSpec bar : bars) {
            int barX = originX + GuiAnchor.resolveX(bar.anchor(), ContainerLayout.PANEL_WIDTH,
                    bar.width(), bar.x());
            int barY = originY + GuiAnchor.resolveY(bar.anchor(), panelH, bar.height(), bar.y());
            if (mouseX >= barX && mouseX <= barX + bar.width()
                    && mouseY >= barY && mouseY <= barY + bar.height()) {
                Component tooltip = formatTooltip(menu, bar);
                if (tooltip != null) {
                    guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY);
                    return true;
                }
            }
        }
        return false;
    }

    private static Component formatTooltip(CustomContainerMenu menu, MachineBarSpec bar) {
        if (bar.currentKey() != null && bar.maxKey() != null) {
            int current = menu.getCustomValue(bar.currentKey());
            int max = menu.getCustomValue(bar.maxKey());
            return customFormat(bar, current, max);
        }
        if (bar.tooltipFormat() != null && !bar.tooltipFormat().isBlank()) {
            return Component.literal(bar.tooltipFormat());
        }
        return sourceTooltip(menu, bar.source());
    }

    private static Component customFormat(MachineBarSpec bar, int current, int max) {
        if (bar.tooltipFormat() != null && !bar.tooltipFormat().isBlank()) {
            try {
                return Component.literal(String.format(bar.tooltipFormat(), current, max));
            } catch (java.util.IllegalFormatException e) {
                return Component.literal(bar.tooltipFormat());
            }
        }
        return Component.literal(String.format("%,d / %,d", current, max));
    }

    private static Component sourceTooltip(CustomContainerMenu menu, String source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case "energy" -> Component.literal(String.format("%,d / %,d FE",
                    menu.getEnergyStored(), menu.getEnergyCapacity()));
            case "fluid" -> Component.literal(String.format("%,d / %,d mB",
                    menu.getFluidAmount(), menu.getFluidCapacity()));
            case "progress" -> Component.literal(String.format("%d%%",
                    Math.round(menu.getProgress() * PERCENT)));
            default -> null;
        };
    }
}
