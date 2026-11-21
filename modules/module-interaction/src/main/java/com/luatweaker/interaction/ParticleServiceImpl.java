package com.luatweaker.interaction;

import com.luatweaker.api.particle.IParticleService;
import com.luatweaker.api.particle.ParticleOptionsSpec;
import com.luatweaker.api.particle.ParticleSpawn;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * World-pipeline implementation of {@link IParticleService}. Geometry lives in
 * {@link ParticleShapes}; this class validates the call and forwards every generated point.
 */
public final class ParticleServiceImpl implements IParticleService {

    private static final int COMPONENTS = 3;
    private static final int MIN_BEZIER_POINTS = 2;

    @Override
    public void spawn(String particleId, double x, double y, double z,
                      double vx, double vy, double vz, int count, double speed, String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        ParticleSpawnSupport.requireCount(count);
        com.luatweaker.api.pal.Platform.getInteraction().spawnParticle(dimension, particleId,
                x, y, z, vx, vy, vz, count, speed);
    }

    @Override
    public void spawnOptions(String particleId, double x, double y, double z,
                             ILuaTable options, int count, double speed, String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        ParticleSpawnSupport.requireCount(count);
        if (options == null) {
            throw new IllegalArgumentException("Particle:SpawnOptions requires an options table");
        }
        ParticleOptionsSpec spec = ParticleOptionsReader.read(options);
        com.luatweaker.api.pal.Platform.getInteraction().spawnParticleOptions(
                dimension, particleId,
                new ParticleSpawn(x, y, z, ParticleSpawnSupport.ZERO_VELOCITY,
                        ParticleSpawnSupport.ZERO_VELOCITY, ParticleSpawnSupport.ZERO_VELOCITY,
                        count, speed),
                spec);
    }

    @Override
    public void line(String particleId, double x1, double y1, double z1,
                     double x2, double y2, double z2, double spacing, double speed,
                     String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        for (double[] point : ParticleShapes.line(x1, y1, z1, x2, y2, z2, spacing)) {
            ParticleSpawnSupport.spawnAt(particleId, point, 1, speed, dimension);
        }
    }

    @Override
    public void circle(String particleId, double centerX, double centerY, double centerZ,
                       double radius, int points, double speed, String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        for (double[] point : ParticleShapes.circle(centerX, centerY, centerZ, radius, points)) {
            ParticleSpawnSupport.spawnAt(particleId, point, 1, speed, dimension);
        }
    }

    @Override
    public void helix(String particleId, double centerX, double centerY, double centerZ,
                      double radius, double height, double turns, int points,
                      double speed, String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        for (double[] point
                : ParticleShapes.helix(centerX, centerY, centerZ, radius, height, turns, points)) {
            ParticleSpawnSupport.spawnAt(particleId, point, 1, speed, dimension);
        }
    }

    @Override
    public void sphere(String particleId, double centerX, double centerY, double centerZ,
                       double radius, int points, double speed, String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        for (double[] point : ParticleShapes.sphere(centerX, centerY, centerZ, radius, points)) {
            ParticleSpawnSupport.spawnAt(particleId, point, 1, speed, dimension);
        }
    }

    @Override
    public void bezier(String particleId, ILuaTable controlPoints, int samples,
                       double speed, String dimension) {
        ParticleSpawnSupport.validate(particleId, dimension);
        for (double[] point : ParticleShapes.bezier(readControlPoints(controlPoints), samples)) {
            ParticleSpawnSupport.spawnAt(particleId, point, 1, speed, dimension);
        }
    }

    private static List<double[]> readControlPoints(@NotNull ILuaTable controlPoints) {
        int length = controlPoints.length();
        List<double[]> points = new ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            points.add(readControlPoint(controlPoints, index));
        }
        if (points.size() < MIN_BEZIER_POINTS) {
            throw new IllegalArgumentException(
                    "Particle:Bezier needs at least " + MIN_BEZIER_POINTS + " control points");
        }
        return points;
    }

    private static double[] readControlPoint(ILuaTable table, int index) {
        ILuaValue value = table.rawget(index);
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(
                    "Particle:Bezier control point #" + index + " is missing");
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(
                    "Particle:Bezier control point #" + index + " must be a Vector3 table");
        }
        double[] point = InteractionVectors.readVector(value);
        if (point.length < COMPONENTS) {
            throw new IllegalArgumentException(
                    "Particle:Bezier control point #" + index + " needs X, Y and Z numbers");
        }
        return point;
    }
}
