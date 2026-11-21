package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal PAL client double: records the sound calls so the binding path (not the platform) is
 * actually exercised.
 */
final class RecordingClient implements IPlatformClient {

    private static final long LOOP_HANDLE = 41L;
    private static final int STOP_ALL_COUNT = 3;

    private final List<String> sounds = new ArrayList<>();
    String lastSoundId;
    double lastVolume;
    double lastPitch;
    String lastSource;
    boolean lastLoop;
    double lastX;
    double lastY;
    double lastZ;
    String lastStoppedSounds;
    String lastStoppedSource;
    int stopAllCalls;
    private boolean handleStopped;

    List<String> playedSounds() {
        return Collections.unmodifiableList(sounds);
    }

    @Override
    public void spawnParticle(String particleId, double x, double y, double z,
                              double vx, double vy, double vz) {
        // Particles are outside this double's concern; the bindings are covered elsewhere.
    }

    @Override
    public long playSound(String soundId, double volume, double pitch, String source, boolean loop) {
        record(soundId, volume, pitch, source, loop);
        return loop ? LOOP_HANDLE : 0L;
    }

    @Override
    public long playSoundAt(String soundId, double x, double y, double z,
                            double volume, double pitch, String source, boolean loop) {
        record(soundId, volume, pitch, source, loop);
        lastX = x;
        lastY = y;
        lastZ = z;
        return loop ? LOOP_HANDLE : 0L;
    }

    private void record(String soundId, double volume, double pitch, String source, boolean loop) {
        sounds.add(soundId);
        lastSoundId = soundId;
        lastVolume = volume;
        lastPitch = pitch;
        lastSource = source;
        lastLoop = loop;
    }

    @Override
    public boolean stopSound(long handle) {
        if (handle != LOOP_HANDLE || handleStopped) {
            return false;
        }
        handleStopped = true;
        return true;
    }

    @Override
    public void stopSounds(String soundId, String source) {
        lastStoppedSounds = soundId;
        lastStoppedSource = source;
    }

    @Override
    public int stopAllSounds() {
        stopAllCalls++;
        return STOP_ALL_COUNT;
    }

    @Override
    public int getActiveSoundCount() {
        return 0;
    }

    @Override
    public int getGuiScaledWidth() {
        return 640;
    }

    @Override
    public int getGuiScaledHeight() {
        return 360;
    }
}
