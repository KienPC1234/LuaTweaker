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

    private ShaderRenderHook() {}

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        try {
            float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
            if (delta == null) {
                return;
            }
            event.setYaw(event.getYaw() + delta[0]);
            event.setPitch(event.getPitch() + delta[1]);
            event.setRoll(event.getRoll() + delta[2]);
        } catch (Throwable t) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Shaders camera hook failed: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        try {
            float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
            if (delta == null || delta[3] == 0.0f) {
                return;
            }
            event.setFOV(event.getFOV() + delta[3]);
        } catch (Throwable t) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Shaders FOV hook failed: " + t.getMessage());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            long nowMs = System.currentTimeMillis();

            int fade = ShaderServiceImpl.getFadeArgb(nowMs);
            if (fade != 0) {
                event.getGuiGraphics().fill(0, 0, width, height, fade);
            }

            double tintStrength = ShaderServiceImpl.getTintStrength();
            if (tintStrength > 0.0) {
                event.getGuiGraphics().fill(0, 0, width, height,
                        withAlpha(ShaderServiceImpl.getTintArgb(), tintStrength));
            }

            double letterbox = ShaderServiceImpl.getLetterboxHeight();
            if (letterbox > 0.0) {
                int bar = (int) (height * Math.min(0.4, letterbox));
                if (bar > 0) {
                    event.getGuiGraphics().fill(0, 0, width, bar, 0xFF000000);
                    event.getGuiGraphics().fill(0, height - bar, width, height, 0xFF000000);
                }
            }

            double vignette = ShaderServiceImpl.getVignetteStrength();
            if (vignette > 0.0) {
                drawEdges(event, width, height, ShaderServiceImpl.getVignetteArgb(), vignette, 3);
            }

            double pulse = ShaderServiceImpl.getPulseStrength();
            if (pulse > 0.0 && isPlayerHurt(mc)) {
                double beat = 0.5 + 0.5 * Math.sin(nowMs / 300.0);
                drawEdges(event, width, height, ShaderServiceImpl.getPulseArgb(), pulse * beat, 2);
            }

            double frost = ShaderServiceImpl.getFrostStrength();
            if (frost > 0.0) {
                drawEdges(event, width, height, 0xFFBFD9FF, frost, 4);
            }

            double blood = ShaderServiceImpl.getBloodStrength();
            if (blood > 0.0) {
                drawEdges(event, width, height, 0xFF660000, blood, 2);
                int bottom = height / 5;
                event.getGuiGraphics().fill(0, height - bottom, width, height,
                        withAlpha(0xFF660000, blood * 0.7));
            }
        } catch (Throwable t) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Shaders overlay hook failed: " + t.getMessage());
        }
    }

    private static boolean isPlayerHurt(Minecraft mc) {
        return mc.player != null && mc.level != null
                && mc.player.getHealth() < mc.player.getMaxHealth() * 0.35f;
    }

    /** Nested edge bands with falling alpha, so the border melts inward. */
    private static void drawEdges(RenderGuiEvent.Post event, int width, int height,
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
