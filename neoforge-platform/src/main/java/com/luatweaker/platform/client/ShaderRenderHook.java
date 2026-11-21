package com.luatweaker.platform.client;

import com.luatweaker.client.ShaderServiceImpl;
import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Applies {@code Shaders} effects on the render thread.
 *
 * <p>Reads the state published by {@link ShaderServiceImpl} (Lua thread writes,
 * render thread reads) and adds camera offsets plus fullscreen overlay rects.
 * Additive with the legacy camera shake: both listeners adjust the same event.
 * Overlays run at {@link EventPriority#LOWEST} so they draw above the legacy
 * screen flash and the Lua HUD.</p>
 */
@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public final class ShaderRenderHook {
    private static final double MIN_EFFECT_STRENGTH = 0.0;
    /** Hard ceiling for the letterbox, so a bad Lua value never covers the whole screen. */
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

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        RenderFirewall.run(() -> applyCameraDelta(event), () -> "Shaders camera hook failed");
    }

    private static void applyCameraDelta(@NotNull ViewportEvent.ComputeCameraAngles event) {
        float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
        if (delta == null) {
            return;
        }
        event.setYaw(event.getYaw() + delta[0]);
        event.setPitch(event.getPitch() + delta[1]);
        event.setRoll(event.getRoll() + delta[2]);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        RenderFirewall.run(() -> applyFovDelta(event), () -> "Shaders FOV hook failed");
    }

    private static void applyFovDelta(@NotNull ViewportEvent.ComputeFov event) {
        float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
        if (delta == null || delta[3] == 0.0f) {
            return;
        }
        event.setFOV(event.getFOV() + delta[3]);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        RenderFirewall.run(() -> drawShaderOverlays(event), () -> "Shaders overlay hook failed");
    }

    private static void drawShaderOverlays(@NotNull RenderGuiEvent.Post event) {
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        long nowMs = System.currentTimeMillis();
        drawFade(event, width, height, nowMs);
        drawTint(event, width, height);
        drawLetterbox(event, width, height);
        drawVignette(event, width, height);
        drawPulse(event, width, height, nowMs);
        drawFrost(event, width, height);
        drawBlood(event, width, height);
    }

    private static void drawFade(@NotNull RenderGuiEvent.Post event, int width, int height, long nowMs) {
        int fade = ShaderServiceImpl.getFadeArgb(nowMs);
        if (fade != 0) {
            event.getGuiGraphics().fill(0, 0, width, height, fade);
        }
    }

    private static void drawTint(@NotNull RenderGuiEvent.Post event, int width, int height) {
        double tintStrength = ShaderServiceImpl.getTintStrength();
        if (tintStrength > MIN_EFFECT_STRENGTH) {
            event.getGuiGraphics().fill(0, 0, width, height,
                    withAlpha(ShaderServiceImpl.getTintArgb(), tintStrength));
        }
    }

    private static void drawLetterbox(@NotNull RenderGuiEvent.Post event, int width, int height) {
        double letterbox = ShaderServiceImpl.getLetterboxHeight();
        if (letterbox <= MIN_EFFECT_STRENGTH) {
            return;
        }
        int bar = (int) (height * Math.min(MAX_LETTERBOX_RATIO, letterbox));
        if (bar > 0) {
            event.getGuiGraphics().fill(0, 0, width, bar, 0xFF000000);
            event.getGuiGraphics().fill(0, height - bar, width, height, 0xFF000000);
        }
    }

    private static void drawVignette(@NotNull RenderGuiEvent.Post event, int width, int height) {
        double vignette = ShaderServiceImpl.getVignetteStrength();
        if (vignette > MIN_EFFECT_STRENGTH) {
            drawEdges(event, width, height, ShaderServiceImpl.getVignetteArgb(), vignette, VIGNETTE_LAYERS);
        }
    }

    private static void drawPulse(@NotNull RenderGuiEvent.Post event, int width, int height, long nowMs) {
        double pulse = ShaderServiceImpl.getPulseStrength();
        if (pulse <= MIN_EFFECT_STRENGTH || !isPlayerHurt(Minecraft.getInstance())) {
            return;
        }
        double beat = 0.5 + 0.5 * Math.sin(nowMs / PULSE_PERIOD_MS);
        drawEdges(event, width, height, ShaderServiceImpl.getPulseArgb(), pulse * beat, PULSE_LAYERS);
    }

    private static void drawFrost(@NotNull RenderGuiEvent.Post event, int width, int height) {
        double frost = ShaderServiceImpl.getFrostStrength();
        if (frost > MIN_EFFECT_STRENGTH) {
            drawEdges(event, width, height, FROST_ARGB, frost, FROST_LAYERS);
        }
    }

    private static void drawBlood(@NotNull RenderGuiEvent.Post event, int width, int height) {
        double blood = ShaderServiceImpl.getBloodStrength();
        if (blood <= MIN_EFFECT_STRENGTH) {
            return;
        }
        drawEdges(event, width, height, BLOOD_ARGB, blood, BLOOD_LAYERS);
        int bottom = (int) (height * BLOOD_BOTTOM_RATIO);
        event.getGuiGraphics().fill(0, height - bottom, width, height,
                withAlpha(BLOOD_ARGB, blood * BLOOD_BOTTOM_ALPHA));
    }

    private static boolean isPlayerHurt(@NotNull Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null
                && minecraft.player.getHealth() < minecraft.player.getMaxHealth() * HURT_HEALTH_RATIO;
    }

    /** Nested edge bands with falling alpha, so the border melts inward. */
    private static void drawEdges(@NotNull RenderGuiEvent.Post event, int width, int height,
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
            var graphics = event.getGuiGraphics();
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
