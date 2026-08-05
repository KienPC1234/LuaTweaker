package com.luatweaker.client;

import com.luatweaker.api.client.ICameraService;
import java.util.Random;

public class CameraServiceImpl implements ICameraService {

    private static double shakeIntensity = 0;
    private static long shakeDurationMs = 0;
    private static long shakeStartTime = 0;
    private static final Random random = new Random();

    @Override
    public void shake(double intensity, double duration) {
        shakeIntensity = intensity;
        shakeDurationMs = (long) (duration * 1000);
        shakeStartTime = System.currentTimeMillis();
    }

    public static float[] getCameraShakeOffsets() {
        if (shakeDurationMs <= 0) return null;

        long elapsed = System.currentTimeMillis() - shakeStartTime;
        if (elapsed >= shakeDurationMs) {
            shakeDurationMs = 0;
            return null;
        }

        // Decay intensity over time
        float progress = (float) elapsed / shakeDurationMs;
        double currentIntensity = shakeIntensity * (1.0 - progress);

        // Calculate random offsets (yaw, pitch, roll)
        float yawOffset = (float) ((random.nextFloat() - 0.5f) * 2.0 * currentIntensity);
        float pitchOffset = (float) ((random.nextFloat() - 0.5f) * 2.0 * currentIntensity);
        float rollOffset = (float) ((random.nextFloat() - 0.5f) * 2.0 * currentIntensity);

        return new float[]{yawOffset, pitchOffset, rollOffset};
    }
}
