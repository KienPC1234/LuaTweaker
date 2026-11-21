package com.luatweaker.client;

import com.luatweaker.api.client.ICameraService;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class CameraServiceImpl implements ICameraService {

    private static final Random RANDOM = new Random();
    private static final double MAX_OFFSET = 2.0;
    private static final double HALF_RANGE = 0.5;
    private static final double MILLIS_PER_SECOND = 1000.0;

    /** One immutable shake snapshot: the server thread writes it, the render thread reads it. */
    private record ShakeState(double intensity, long durationMs, long startTime) {}

    private static final ShakeState NO_SHAKE = new ShakeState(0.0, 0, 0);
    private static final AtomicReference<ShakeState> SHAKE_STATE = new AtomicReference<>(NO_SHAKE);

    @Override
    public void shake(double intensity, double duration) {
        if (intensity <= 0 || duration <= 0) {
            stop();
            return;
        }
        SHAKE_STATE.set(new ShakeState(intensity, (long) (duration * MILLIS_PER_SECOND),
                System.currentTimeMillis()));
    }

    @Override
    public void stop() {
        SHAKE_STATE.set(NO_SHAKE);
    }

    /** Render-thread shake offsets, or null when no shake is active or it has expired. */
    @Nullable
    public static float[] getCameraShakeOffsets() {
        ShakeState state = SHAKE_STATE.get();
        long duration = state.durationMs();
        if (duration <= 0) {
            return null;
        }

        long elapsed = System.currentTimeMillis() - state.startTime();
        if (elapsed >= duration) {
            SHAKE_STATE.compareAndSet(state, NO_SHAKE);
            return null;
        }

        // Decay intensity over time.
        float progress = (float) elapsed / duration;
        double currentIntensity = state.intensity() * (1.0 - progress);

        // Calculate random offsets (yaw, pitch, roll).
        float yawOffset = (float) ((RANDOM.nextFloat() - HALF_RANGE) * MAX_OFFSET * currentIntensity);
        float pitchOffset = (float) ((RANDOM.nextFloat() - HALF_RANGE) * MAX_OFFSET * currentIntensity);
        float rollOffset = (float) ((RANDOM.nextFloat() - HALF_RANGE) * MAX_OFFSET * currentIntensity);

        return new float[] { yawOffset, pitchOffset, rollOffset };
    }
}
