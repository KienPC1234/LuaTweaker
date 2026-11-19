package com.luatweaker.client;

import com.luatweaker.api.client.ICameraService;
import java.util.Random;

public class CameraServiceImpl implements ICameraService {

    // Written from the server thread, read every frame by the render thread.
    private static volatile double shakeIntensity;
    private static volatile long shakeDurationMs;
    private static volatile long shakeStartTime;
    private static final Random random = new Random();

    @Override
    public void shake(double intensity, double duration) {
        if (intensity <= 0 || duration <= 0) {
            stop();
            return;
        }
        shakeIntensity = intensity;
        shakeDurationMs = (long) (duration * 1000.0);
        shakeStartTime = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        shakeIntensity = 0;
        shakeDurationMs = 0;
        shakeStartTime = 0;
    }

    public static float[] getCameraShakeOffsets() {
        long duration = shakeDurationMs;
        if (duration <= 0) {
            return null;
        }

        long elapsed = System.currentTimeMillis() - shakeStartTime;
        if (elapsed >= duration) {
            shakeDurationMs = 0;
            return null;
        }

        // Decay intensity over time
        float progress = (float) elapsed / duration;
        double currentIntensity = shakeIntensity * (1.0 - progress);

        // Calculate random offsets (yaw, pitch, roll)
        float yawOffset = (float) ((random.nextFloat() - 0.5f) * 2.0 * currentIntensity);
        float pitchOffset = (float) ((random.nextFloat() - 0.5f) * 2.0 * currentIntensity);
        float rollOffset = (float) ((random.nextFloat() - 0.5f) * 2.0 * currentIntensity);

        return new float[] { yawOffset, pitchOffset, rollOffset };
    }
}
