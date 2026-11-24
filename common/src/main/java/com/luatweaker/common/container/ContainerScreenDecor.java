package com.luatweaker.common.container;

import com.luatweaker.api.content.MachineBarSpec;
import com.luatweaker.api.content.MachineLabelSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Bar/label/tooltip drawing for {@link CustomContainerScreen}. */
final class ContainerScreenDecor {
    /** Semi-transparent dark background painted under every GUI bar (engine-fixed). */
    private static final int BAR_BACKGROUND = 0x7F101010;
    /** Default ARGB fill colors per bar source when the Lua config passes 0. */
    private static final int DEFAULT_ENERGY_COLOR = 0xFF00E676;
    private static final int DEFAULT_FLUID_COLOR = 0xFF2196F3;
    private static final int DEFAULT_PROGRESS_COLOR = 0xFFFFC107;
    private static final float MIN_FILL_RATIO = 0.0f;
    private static final float MAX_FILL_RATIO = 1.0f;

    private ContainerScreenDecor() {
    }

    static void renderBars(GuiGraphics guiGraphics, CustomContainerMenu menu, List<MachineBarSpec> bars,
                           int originX, int originY) {
        int panelW = ContainerLayout.PANEL_WIDTH;
        int panelH = ContainerLayout.imageHeight(menu.getContainerRows());
        for (MachineBarSpec bar : bars) {
            int barW = ContainerBounds.clampWidth(bar.width(), panelW);
            int barH = ContainerBounds.clampHeight(bar.height(), panelH);
            int relX = ContainerBounds.clampX(GuiAnchor.resolveX(bar.anchor(), panelW, barW, bar.x()), barW, panelW);
            int relY = ContainerBounds.clampY(GuiAnchor.resolveY(bar.anchor(), panelH, barH, bar.y()), barH, panelH);
            int barX = originX + relX;
            int barY = originY + relY;
            int color = bar.color() == 0 ? defaultBarColor(bar.source()) : (bar.color() | 0xFF000000);
            guiGraphics.fill(barX, barY, barX + barW, barY + barH, BAR_BACKGROUND);
            float ratio = barRatio(menu, bar);
            if (ratio > MIN_FILL_RATIO) {
                fillClampedBar(guiGraphics, barX, barY, barW, barH, ratio, color);
            }
        }
    }

    private static void fillClampedBar(GuiGraphics guiGraphics, int barX, int barY,
                                       int barW, int barH, float ratio, int color) {
        if (barW >= barH) {
            int fillWidth = Math.round(barW * ratio);
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barH, color);
        } else {
            int fillHeight = Math.round(barH * ratio);
            guiGraphics.fill(barX, barY + barH - fillHeight, barX + barW, barY + barH, color);
        }
    }

    private static float barRatio(CustomContainerMenu menu, MachineBarSpec bar) {
        if (bar.currentKey() != null && bar.maxKey() != null) {
            int current = menu.getCustomValue(bar.currentKey());
            int max = menu.getCustomValue(bar.maxKey());
            return max > 0 ? Math.min(MAX_FILL_RATIO, Math.max(MIN_FILL_RATIO, (float) current / max)) : 0f;
        }
        return sourceRatio(menu, bar.source());
    }

    private static float sourceRatio(CustomContainerMenu menu, String source) {
        if (source == null) {
            return 0f;
        }
        return switch (source) {
            case "energy" -> menu.getEnergyCapacity() > 0
                    ? Math.min(MAX_FILL_RATIO, Math.max(MIN_FILL_RATIO, (float) menu.getEnergyStored() / menu.getEnergyCapacity())) : 0f;
            case "fluid" -> menu.getFluidCapacity() > 0
                    ? Math.min(MAX_FILL_RATIO, Math.max(MIN_FILL_RATIO, (float) menu.getFluidAmount() / menu.getFluidCapacity())) : 0f;
            case "progress" -> Math.min(MAX_FILL_RATIO, Math.max(MIN_FILL_RATIO, menu.getProgress()));
            default -> 0f;
        };
    }

    private static int defaultBarColor(String source) {
        if (source == null) {
            return DEFAULT_ENERGY_COLOR;
        }
        return switch (source) {
            case "energy" -> DEFAULT_ENERGY_COLOR;
            case "fluid" -> DEFAULT_FLUID_COLOR;
            case "progress" -> DEFAULT_PROGRESS_COLOR;
            default -> DEFAULT_ENERGY_COLOR;
        };
    }

    static void renderLabels(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu) {
        if (menu.getType() == null) {
            return;
        }
        List<MachineLabelSpec> labels = CustomContainerRegistry.CONTAINER_LABELS.get(menu.getType());
        if (labels == null) {
            return;
        }
        ContainerLabelRenderer.renderLabels(guiGraphics, font, menu, labels);
    }
}



