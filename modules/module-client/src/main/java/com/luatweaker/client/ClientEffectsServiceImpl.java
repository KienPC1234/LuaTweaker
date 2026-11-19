package com.luatweaker.client;

import com.luatweaker.api.client.IClientEffectsService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;

public class ClientEffectsServiceImpl implements IClientEffectsService {

    private static final int RGB_HEX_LENGTH = 6;
    private static final int ARGB_HEX_LENGTH = 8;

    // The server thread writes these and the render thread reads them every frame.
    private static volatile int flashColor;
    private static volatile long flashStartTime;
    private static volatile long flashDurationMs;

    @Override
    public void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz) {
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "ClientEffects:SpawnParticle ignored on a dedicated server ('" + particleId + "')");
            return;
        }
        client.spawnParticle(particleId, x, y, z, vx, vy, vz);
    }

    @Override
    public long playSound(String soundId, double volume, double pitch, String source, boolean loop) {
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "ClientEffects:PlaySound ignored on a dedicated server ('" + soundId + "')");
            return 0L;
        }
        return client.playSound(soundId, volume, pitch, source, loop);
    }

    @Override
    public long playSoundAt(String soundId, double x, double y, double z,
                            double volume, double pitch, String source, boolean loop) {
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "ClientEffects:PlaySoundAt ignored on a dedicated server ('" + soundId + "')");
            return 0L;
        }
        return client.playSoundAt(soundId, x, y, z, volume, pitch, source, loop);
    }

    @Override
    public boolean stopSound(long handle) {
        IPlatformClient client = Platform.getClientOrNull();
        return client != null && client.stopSound(handle);
    }

    @Override
    public void stopSounds(String soundId, String source) {
        IPlatformClient client = Platform.getClientOrNull();
        if (client != null) {
            client.stopSounds(soundId, source);
        }
    }

    @Override
    public int stopAllSounds() {
        IPlatformClient client = Platform.getClientOrNull();
        return client == null ? 0 : client.stopAllSounds();
    }

    @Override
    public int getActiveSoundCount() {
        IPlatformClient client = Platform.getClientOrNull();
        return client == null ? 0 : client.getActiveSoundCount();
    }

    @Override
    public void flashScreen(String hexColor, double duration) {
        if (hexColor == null || hexColor.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "FlashScreen ignored: empty color");
            return;
        }
        if (duration < 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "FlashScreen ignored: negative duration " + duration);
            return;
        }
        String hex = hexColor.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != RGB_HEX_LENGTH && hex.length() != ARGB_HEX_LENGTH) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "FlashScreen ignored: expected #RRGGBB or #AARRGGBB, got '" + hexColor + "'");
            return;
        }
        int color;
        try {
            color = (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "FlashScreen ignored: '" + hexColor + "' is not a hex color");
            return;
        }
        if (hex.length() == RGB_HEX_LENGTH) {
            color |= 0xFF000000;
        }
        flashColor = color;
        flashDurationMs = (long) (duration * 1000.0);
        flashStartTime = System.currentTimeMillis();
    }

    /** Alpha of the active flash for the render thread, or 0 when nothing is flashing. */
    public static int getActiveFlashColor() {
        long duration = flashDurationMs;
        if (duration <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - flashStartTime;
        if (elapsed >= duration) {
            flashDurationMs = 0;
            return 0;
        }
        int color = flashColor;
        float progress = (float) elapsed / duration;
        int originalAlpha = (color >> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * (1.0f - progress));
        if (newAlpha > 0) {
            return (newAlpha << 24) | (color & 0x00FFFFFF);
        }
        return 0;
    }
}
