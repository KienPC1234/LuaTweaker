package com.luatweaker.platform.container;

import com.luatweaker.api.content.MachineBarSpec;
import com.luatweaker.api.content.MachineLabelSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
    private static final int PERCENT = 100;

    private ContainerScreenDecor() {
    }

    static void renderBars(GuiGraphics guiGraphics, CustomContainerMenu menu, List<MachineBarSpec> bars,
                           int originX, int originY) {
        for (MachineBarSpec bar : bars) {
            int barX = originX + bar.x();
            int barY = originY + bar.y();
            int color = bar.color() > 0 ? bar.color() : defaultBarColor(bar.source());
            guiGraphics.fill(barX, barY, barX + bar.width(), barY + bar.height(), BAR_BACKGROUND);
            float ratio = barRatio(menu, bar);
            if (ratio > MIN_FILL_RATIO) {
                fillBar(guiGraphics, bar, barX, barY, ratio, color);
            }
        }
    }

    private static void fillBar(GuiGraphics guiGraphics, MachineBarSpec bar, int barX, int barY,
                                float ratio, int color) {
        if (bar.width() >= bar.height()) {
            int fillWidth = Math.round(bar.width() * ratio);
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + bar.height(), color);
        } else {
            int fillHeight = Math.round(bar.height() * ratio);
            guiGraphics.fill(barX, barY + bar.height() - fillHeight, barX + bar.width(),
                    barY + bar.height(), color);
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
        for (MachineLabelSpec label : labels) {
            guiGraphics.drawString(font, label.textOrVar(), label.x(), label.y(), label.color(), false);
        }
    }

    /** Renders the tooltip of the bar under the cursor; returns true when one was drawn. */
    static boolean renderBarTooltip(GuiGraphics guiGraphics, Font font, CustomContainerMenu menu,
                                    List<MachineBarSpec> bars, int mouseX, int mouseY,
                                    int originX, int originY) {
        for (MachineBarSpec bar : bars) {
            int barX = originX + bar.x();
            int barY = originY + bar.y();
            if (mouseX >= barX && mouseX <= barX + bar.width()
                    && mouseY >= barY && mouseY <= barY + bar.height()) {
                Component tooltip = formatBarTooltip(menu, bar);
                if (tooltip != null) {
                    guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY);
                    return true;
                }
            }
        }
        return false;
    }

    private static Component formatBarTooltip(CustomContainerMenu menu, MachineBarSpec bar) {
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



