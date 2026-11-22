package com.luatweaker.common.client;

import com.luatweaker.client.ShaderServiceImpl;
import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

public final class ShaderRenderHook {
    private static final double MIN_EFFECT_STRENGTH = 0.0;
    private static final double MAX_LETTERBOX_RATIO = 0.4;
    private static final double PULSE_PERIOD_MS = 300.0;
    private static final int VIGNETTE_LAYERS = 3;
    private static final int PULSE_LAYERS = 2;
    private static final int FROST_LAYERS = 4;
    private static final int BLOOD_LAYERS = 2;
    private static final int BLOOD_ARGB = 0xFF660000;
    private static final int FROST_ARGB = 0xFFBFD9FF;
    private static final double BLOOD_BOTTOM_RATIO = 0.2;
    private static final double BLOOD_BOTTOM_ALPHA = 0.7;
    private static final float HURT_HEALTH_RATIO = 0.35f;

    private ShaderRenderHook() {}

    public static void init() {
        ClientGuiEvent.RENDER_HUD.register((graphics, partialTick) -> onRenderGui(graphics));
    }

    public static void onRenderGui(GuiGraphics graphics) {
        RenderFirewall.run(() -> drawShaderOverlays(graphics), () -> "Shaders overlay hook failed");
    }

    public static void drawShaderOverlays(@NotNull GuiGraphics graphics) {
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        long nowMs = System.currentTimeMillis();
        drawFade(graphics, width, height, nowMs);
        drawTint(graphics, width, height);
        drawLetterbox(graphics, width, height);
        drawVignette(graphics, width, height);
        drawPulse(graphics, width, height, nowMs);
        drawFrost(graphics, width, height);
        drawBlood(graphics, width, height);
    }

    private static void drawFade(@NotNull GuiGraphics graphics, int width, int height, long nowMs) {
        int fade = ShaderServiceImpl.getFadeArgb(nowMs);
        if (fade != 0) {
            graphics.fill(0, 0, width, height, fade);
        }
    }

    private static void drawTint(@NotNull GuiGraphics graphics, int width, int height) {
        double tintStrength = ShaderServiceImpl.getTintStrength();
        if (tintStrength > MIN_EFFECT_STRENGTH) {
            graphics.fill(0, 0, width, height,
                    withAlpha(ShaderServiceImpl.getTintArgb(), tintStrength));
        }
    }

    private static void drawLetterbox(@NotNull GuiGraphics graphics, int width, int height) {
        double letterbox = ShaderServiceImpl.getLetterboxHeight();
        if (letterbox <= MIN_EFFECT_STRENGTH) {
            return;
        }
        int bar = (int) (height * Math.min(MAX_LETTERBOX_RATIO, letterbox));
        if (bar > 0) {
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
    }

    private static void drawVignette(@NotNull GuiGraphics graphics, int width, int height) {
        double vignette = ShaderServiceImpl.getVignetteStrength();
        if (vignette > MIN_EFFECT_STRENGTH) {
            drawEdges(graphics, width, height, ShaderServiceImpl.getVignetteArgb(), vignette, VIGNETTE_LAYERS);
        }
    }

    private static void drawPulse(@NotNull GuiGraphics graphics, int width, int height, long nowMs) {
        double pulse = ShaderServiceImpl.getPulseStrength();
        if (pulse <= MIN_EFFECT_STRENGTH || !isPlayerHurt(Minecraft.getInstance())) {
            return;
        }
        double beat = 0.5 + 0.5 * Math.sin(nowMs / PULSE_PERIOD_MS);
        drawEdges(graphics, width, height, ShaderServiceImpl.getPulseArgb(), pulse * beat, PULSE_LAYERS);
    }

    private static void drawFrost(@NotNull GuiGraphics graphics, int width, int height) {
        double frost = ShaderServiceImpl.getFrostStrength();
        if (frost > MIN_EFFECT_STRENGTH) {
            drawEdges(graphics, width, height, FROST_ARGB, frost, FROST_LAYERS);
        }
    }

    private static void drawBlood(@NotNull GuiGraphics graphics, int width, int height) {
        double blood = ShaderServiceImpl.getBloodStrength();
        if (blood <= MIN_EFFECT_STRENGTH) {
            return;
        }
        drawEdges(graphics, width, height, BLOOD_ARGB, blood, BLOOD_LAYERS);
        int bottom = (int) (height * BLOOD_BOTTOM_RATIO);
        graphics.fill(0, height - bottom, width, height,
                withAlpha(BLOOD_ARGB, blood * BLOOD_BOTTOM_ALPHA));
    }

    private static boolean isPlayerHurt(@NotNull Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null
                && minecraft.player.getHealth() < minecraft.player.getMaxHealth() * HURT_HEALTH_RATIO;
    }

    private static void drawEdges(@NotNull GuiGraphics graphics, int width, int height,
                                  int argb, double strength, int layers) {
        int band = Math.max(8, Math.min(width, height) / 10);
        for (int layer = 0; layer < layers; layer++) {
            int inset = layer * band / layers;
            double layerStrength = strength * (1.0 - (double) layer / layers);
            int color = withAlpha(argb, layerStrength);
            if (((color >>> 24) & 0xFF) == 0) {
                continue;
            }
            int thickness = Math.max(2, band / layers);
            graphics.fill(inset, inset, width - inset, inset + thickness, color);
            graphics.fill(inset, height - inset - thickness, width - inset, height - inset, color);
            graphics.fill(inset, inset + thickness, inset + thickness, height - inset - thickness, color);
            graphics.fill(width - inset - thickness, inset + thickness, width - inset,
                    height - inset - thickness, color);
        }
    }

    private static int withAlpha(int argb, double strength) {
        int alpha = (int) (255.0 * Math.min(1.0, Math.max(0.0, strength)));
        if (alpha == 0) {
            return 0;
        }
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
