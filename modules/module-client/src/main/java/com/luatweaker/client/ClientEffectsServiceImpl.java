package com.luatweaker.client;

import com.luatweaker.api.client.IClientEffectsService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class ClientEffectsServiceImpl implements IClientEffectsService {

    private static final int RGB_HEX_LENGTH = 6;
    private static final int ARGB_HEX_LENGTH = 8;
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final int ALPHA_SHIFT = 24;
    private static final int RGB_MASK = 0x00FFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF000000;

    /** One immutable flash snapshot: the server thread writes it, the render thread reads it. */
    private record FlashState(int color, long startTime, long durationMs) {}

    private static final FlashState NO_FLASH = new FlashState(0, 0, 0);
    private static final AtomicReference<FlashState> FLASH = new AtomicReference<>(NO_FLASH);

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
        Integer color = parseFlashColor(hexColor);
        if (color == null) {
            return;
        }
        FLASH.set(new FlashState(color, System.currentTimeMillis(),
                (long) (duration * MILLIS_PER_SECOND)));
    }

    /** Parses #RRGGBB / #AARRGGBB (with optional 0x prefix), logging why an input is rejected. */
    private static @Nullable Integer parseFlashColor(@NotNull String hexColor) {
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
            return null;
        }
        int color;
        try {
            color = (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "FlashScreen ignored: '" + hexColor + "' is not a hex color");
            return null;
        }
        if (hex.length() == RGB_HEX_LENGTH) {
            color |= OPAQUE_ALPHA;
        }
        return color;
    }

    /** Alpha of the active flash for the render thread, or 0 when nothing is flashing. */
    public static int getActiveFlashColor() {
        FlashState state = FLASH.get();
        long duration = state.durationMs();
        if (duration <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - state.startTime();
        if (elapsed >= duration) {
            FLASH.compareAndSet(state, NO_FLASH);
            return 0;
        }
        float progress = (float) elapsed / duration;
        int originalAlpha = (state.color() >> ALPHA_SHIFT) & 0xFF;
        int newAlpha = (int) (originalAlpha * (1.0f - progress));
        if (newAlpha > 0) {
            return (newAlpha << ALPHA_SHIFT) | (state.color() & RGB_MASK);
        }
        return 0;
    }
}
