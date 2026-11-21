package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import org.jetbrains.annotations.Nullable;
import java.io.File;

abstract class ShaderServiceBase7 extends ShaderServiceBase6 {

    protected ShaderServiceBase7(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
    }

    static void addSwayDelta(CameraDelta delta, long nowMs) {
        if (SWAY_START_MS.get() == IDLE_TIME_MS) {
            return;
        }
        double elapsed = (nowMs - SWAY_START_MS.get()) / MILLIS_PER_SECOND;
        if (elapsed >= SWAY_SECONDS.get() || SWAY_SECONDS.get() <= ZERO_SECONDS) {
            SWAY_START_MS.set(IDLE_TIME_MS);
            return;
        }
        double phase = elapsed / SWAY_SECONDS.get() * Math.PI * 2.0;
        delta.yaw += (float) (SWAY_AMPLITUDE.get() * Math.sin(phase));
        delta.pitch += (float) (SWAY_AMPLITUDE.get() * 0.6 * Math.cos(phase));
        delta.any = true;
    }

    static void addFovDelta(CameraDelta delta, long nowMs) {
        if (FOV_START_MS.get() == IDLE_TIME_MS) {
            return;
        }
        double elapsed = (nowMs - FOV_START_MS.get()) / MILLIS_PER_SECOND;
        if (elapsed >= FOV_SECONDS.get() || FOV_SECONDS.get() <= ZERO_SECONDS) {
            FOV_START_MS.set(IDLE_TIME_MS);
            return;
        }
        delta.fov += (float) (FOV_AMOUNT.get() * (1.0 - elapsed / FOV_SECONDS.get()));
        delta.any = true;
    }

    public static float @Nullable [] getCameraDelta(long nowMs) {
        CameraDelta delta = new CameraDelta();
        addKickDelta(delta, nowMs);
        addRollDelta(delta, nowMs);
        addSwayDelta(delta, nowMs);
        addFovDelta(delta, nowMs);
        return delta.any ? new float[] { delta.yaw, delta.pitch, delta.roll, delta.fov } : null;
    }

    public static int getFadeArgb(long nowMs) {
        if (!FADE_ACTIVE.get()) {
            return 0;
        }
        long ramp = Math.max(1L, FADE_RAMP_MS.get());
        float progress = Math.min(1.0f, (float) (nowMs - FADE_START_MS.get()) / ramp);
        int baseAlpha = (FADE_ARGB.get() >>> 24) & 0xFF;
        int alpha = (int) (baseAlpha * progress);
        if (alpha <= 0) {
            return 0;
        }
        return (alpha << 24) | (FADE_ARGB.get() & 0x00FFFFFF);
    }

    public static double getLetterboxHeight() {
        return LETTERBOX_HEIGHT.get();
    }
}
