package com.luatweaker.client;

import com.luatweaker.api.client.IClientEffectsService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;

public class ClientEffectsServiceImpl implements IClientEffectsService {

    // Flash screen state
    private static int flashColor = 0;
    private static long flashStartTime = 0;
    private static long flashDurationMs = 0;

    @Override
    public void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz) {
        if (Platform.getClient() != null) {
            Platform.getClient().spawnParticle(particleId, x, y, z, vx, vy, vz);
        }
    }

    @Override
    public void playSound(String soundId, double volume, double pitch) {
        if (Platform.getClient() != null) {
            Platform.getClient().playSound(soundId, volume, pitch);
        }
    }

    @Override
    public void flashScreen(String hexColor, double duration) {
        try {
            String hex = hexColor.startsWith("0x") ? hexColor.substring(2) : hexColor;
            hex = hex.startsWith("#") ? hex.substring(1) : hex;
            // Parse as long to handle ARGB 8-digit hex values
            int color = (int) Long.parseLong(hex, 16);
            
            // If no alpha provided, assume fully opaque
            if (hex.length() <= 6) {
                color |= 0xFF000000;
            }

            flashColor = color;
            flashDurationMs = (long) (duration * 1000);
            flashStartTime = System.currentTimeMillis();
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to parse flash color '" + hexColor + "': " + e.getMessage());
        }
    }

    // Called from NeoForgeClientEventListener via reflection or direct method depending on architecture.
    // Actually we can't use GuiGraphics here either because it's a Minecraft class.
    // We should expose a way to get the current flash color/alpha and do the drawing in NeoForgeClientEventListener!
    
    public static int getActiveFlashColor() {
        if (flashDurationMs <= 0) return 0; // 0 alpha

        long elapsed = System.currentTimeMillis() - flashStartTime;
        if (elapsed >= flashDurationMs) {
            flashDurationMs = 0;
            return 0; // 0 alpha
        }

        // Fade out alpha
        float progress = (float) elapsed / flashDurationMs;
        int originalAlpha = (flashColor >> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * (1.0f - progress));

        if (newAlpha > 0) {
            return (newAlpha << 24) | (flashColor & 0x00FFFFFF);
        }
        return 0;
    }
}
