package com.luatweaker.interaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records the World bulk-edit / structure / effect calls as immutable value objects, so assertions
 * can compare the whole argument list instead of individual fields. Tests may set the {@code *Result}
 * fields to emulate a platform rejection.
 */
final class WorldEditRecorder implements MockPlatformInteraction {

    long fillResult = 42;
    long replaceResult = 7;
    boolean placeResult = true;
    final List<FillCall> fillCalls = new ArrayList<>();
    final List<ReplaceCall> replaceCalls = new ArrayList<>();
    final List<PlaceCall> placeCalls = new ArrayList<>();
    final List<ParticleCall> particleCalls = new ArrayList<>();
    final List<SoundCall> soundCalls = new ArrayList<>();
    final List<StopCall> stopCalls = new ArrayList<>();

    @Override
    public long fillBlocks(String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                           String blockId, Map<String, Object> properties) {
        fillCalls.add(new FillCall(dimension, List.of(x1, y1, z1, x2, y2, z2), blockId, properties));
        return fillResult;
    }

    @Override
    public long replaceBlocks(String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                              String fromId, String toId) {
        replaceCalls.add(new ReplaceCall(dimension, List.of(x1, y1, z1, x2, y2, z2), fromId, toId));
        return replaceResult;
    }

    @Override
    public boolean placeStructure(String dimension, String templateId, int x, int y, int z,
                                  int rotationDegrees) {
        placeCalls.add(new PlaceCall(dimension, templateId, List.of(x, y, z, rotationDegrees)));
        return placeResult;
    }

    @Override
    public void spawnParticle(String dimension, String particleId, double x, double y, double z,
                              double vx, double vy, double vz, int count, double speed) {
        particleCalls.add(new ParticleCall(dimension, particleId, List.of(x, y, z),
                List.of(vx, vy, vz), count, speed));
    }

    @Override
    public void playSound(String dimension, String soundId, double x, double y, double z,
                          float volume, float pitch, String source) {
        soundCalls.add(new SoundCall(dimension, soundId, List.of(x, y, z), volume, pitch, source));
    }

    @Override
    public boolean stopSound(String dimension, String soundId, String source) {
        stopCalls.add(new StopCall(dimension, soundId, source));
        return true;
    }

    record FillCall(String dimension, List<Integer> coords, String blockId,
                    Map<String, Object> properties) {
    }

    record ReplaceCall(String dimension, List<Integer> coords, String fromId, String toId) {
    }

    record PlaceCall(String dimension, String templateId, List<Integer> args) {
    }

    record ParticleCall(String dimension, String particleId, List<Double> position,
                        List<Double> velocity, int count, double speed) {
    }

    record SoundCall(String dimension, String soundId, List<Double> position, float volume,
                     float pitch, String source) {
    }

    record StopCall(String dimension, String soundId, String source) {
    }
}
