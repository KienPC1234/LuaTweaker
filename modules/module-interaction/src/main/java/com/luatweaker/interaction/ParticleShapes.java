package com.luatweaker.interaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure point generators behind the {@code Particle} service.
 *
 * <p>Every method validates its inputs and throws {@link IllegalArgumentException} with a
 * descriptive message instead of producing a silently empty or degenerate shape.</p>
 */
final class ParticleShapes {

    private static final int MIN_CIRCLE_POINTS = 3;
    private static final int MIN_HELIX_POINTS = 2;
    private static final int MIN_SPHERE_POINTS = 1;
    private static final int MIN_BEZIER_SAMPLES = 2;
    private static final int MIN_BEZIER_POINTS = 2;
    private static final int MIN_POINT_COMPONENTS = 3;
    private static final double FULL_TURN = Math.PI * 2.0;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private ParticleShapes() {
    }

    static List<double[]> line(double x1, double y1, double z1,
                               double x2, double y2, double z2, double spacing) {
        ShapeGuard.finite("line x1", x1);
        ShapeGuard.finite("line y1", y1);
        ShapeGuard.finite("line z1", z1);
        ShapeGuard.finite("line x2", x2);
        ShapeGuard.finite("line y2", y2);
        ShapeGuard.finite("line z2", z2);
        ShapeGuard.positive("Particle:Line spacing", spacing);
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int segments = Math.max(1, (int) Math.ceil(length / spacing));
        ShapeGuard.pointCount("Line", segments + 1, 2);
        List<double[]> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            points.add(new double[] { x1 + dx * t, y1 + dy * t, z1 + dz * t });
        }
        return points;
    }

    static List<double[]> circle(double centerX, double centerY, double centerZ,
                                 double radius, int points) {
        ShapeGuard.center("circle", centerX, centerY, centerZ);
        ShapeGuard.radius("circle", radius);
        ShapeGuard.pointCount("circle", points, MIN_CIRCLE_POINTS);
        List<double[]> result = new ArrayList<>(points);
        double step = FULL_TURN / points;
        for (int i = 0; i < points; i++) {
            double angle = step * i;
            result.add(new double[] {
                centerX + Math.cos(angle) * radius,
                centerY,
                centerZ + Math.sin(angle) * radius
            });
        }
        return result;
    }

    static List<double[]> helix(double centerX, double centerY, double centerZ,
                                double radius, double height, double turns, int points) {
        ShapeGuard.center("helix", centerX, centerY, centerZ);
        ShapeGuard.radius("helix", radius);
        ShapeGuard.nonNegative("Particle:Helix height", height);
        ShapeGuard.positive("Particle:Helix turns", turns);
        ShapeGuard.pointCount("helix", points, MIN_HELIX_POINTS);
        List<double[]> result = new ArrayList<>(points);
        double maxAngle = FULL_TURN * turns;
        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            double angle = maxAngle * t;
            result.add(new double[] {
                centerX + Math.cos(angle) * radius,
                centerY + height * t,
                centerZ + Math.sin(angle) * radius
            });
        }
        return result;
    }

    static List<double[]> sphere(double centerX, double centerY, double centerZ,
                                 double radius, int points) {
        ShapeGuard.center("sphere", centerX, centerY, centerZ);
        ShapeGuard.radius("sphere", radius);
        ShapeGuard.pointCount("sphere", points, MIN_SPHERE_POINTS);
        List<double[]> result = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double y = points == 1 ? 0.0 : 1.0 - 2.0 * i / (points - 1);
            double ringRadius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = GOLDEN_ANGLE * i;
            result.add(new double[] {
                centerX + Math.cos(theta) * ringRadius * radius,
                centerY + y * radius,
                centerZ + Math.sin(theta) * ringRadius * radius
            });
        }
        return result;
    }

    static List<double[]> bezier(List<double[]> controlPoints, int samples) {
        if (controlPoints == null || controlPoints.size() < MIN_BEZIER_POINTS) {
            throw new IllegalArgumentException(
                    "Particle:Bezier needs at least " + MIN_BEZIER_POINTS + " control points");
        }
        for (int i = 0; i < controlPoints.size(); i++) {
            requirePoint(controlPoints.get(i), "Particle:Bezier control point #" + (i + 1));
        }
        ShapeGuard.pointCount("bezier", samples, MIN_BEZIER_SAMPLES);
        List<double[]> result = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            double t = (double) i / (samples - 1);
            result.add(deCasteljau(controlPoints, t));
        }
        return result;
    }

    private static double[] deCasteljau(List<double[]> controlPoints, double t) {
        int size = controlPoints.size();
        double[][] working = new double[size][3];
        for (int i = 0; i < size; i++) {
            working[i][0] = controlPoints.get(i)[0];
            working[i][1] = controlPoints.get(i)[1];
            working[i][2] = controlPoints.get(i)[2];
        }
        for (int level = 1; level < size; level++) {
            for (int i = 0; i < size - level; i++) {
                working[i][0] += (working[i + 1][0] - working[i][0]) * t;
                working[i][1] += (working[i + 1][1] - working[i][1]) * t;
                working[i][2] += (working[i + 1][2] - working[i][2]) * t;
            }
        }
        return working[0];
    }

    private static void requirePoint(double[] point, String label) {
        if (point == null || point.length < MIN_POINT_COMPONENTS) {
            throw new IllegalArgumentException(label + " must be a Vector3-shaped array");
        }
        ShapeGuard.finite(label, point[0]);
        ShapeGuard.finite(label, point[1]);
        ShapeGuard.finite(label, point[2]);
    }
}
