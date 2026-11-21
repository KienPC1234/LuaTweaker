package com.luatweaker.math;

import java.util.List;

/**
 * Pure path samplers shared by the Lua {@code Curve} table.
 *
 * <p>All methods return a new {@code {x, y, z}} array and never mutate the control points.
 * Invalid t values (NaN/Infinity, or a t outside [0, 1]) are rejected so a script mistake
 * does not teleport an entity to an undefined position.</p>
 */
final class Curves {

    private static final int EXPECTED_LINE_POINTS = 2;
    private static final int POINT_COMPONENTS = 3;

    private Curves() {
    }

    static double[] line(double t, double[]... points) {
        requireT(t);
        if (points == null || points.length != EXPECTED_LINE_POINTS) {
            throw new IllegalArgumentException("Curve.Line needs exactly two points");
        }
        double[] from = points[0];
        double[] to = points[1];
        requirePoint(from, "Curve.Line from");
        requirePoint(to, "Curve.Line to");
        return new double[] {
            from[0] + (to[0] - from[0]) * t,
            from[1] + (to[1] - from[1]) * t,
            from[2] + (to[2] - from[2]) * t
        };
    }

    static double[] bezier(double t, List<double[]> controlPoints) {
        requireT(t);
        requirePoints(controlPoints, 2, "Curve.Bezier");
        int size = controlPoints.size();
        double[][] working = copy(controlPoints);
        for (int level = 1; level < size; level++) {
            for (int i = 0; i < size - level; i++) {
                working[i][0] += (working[i + 1][0] - working[i][0]) * t;
                working[i][1] += (working[i + 1][1] - working[i][1]) * t;
                working[i][2] += (working[i + 1][2] - working[i][2]) * t;
            }
        }
        return working[0];
    }

    static double[] catmullRom(double t, List<double[]> points) {
        requireT(t);
        requirePoints(points, 2, "Curve.CatmullRom");
        for (int i = 0; i < points.size(); i++) {
            requirePoint(points.get(i), "Curve control point #" + (i + 1));
        }
        int last = points.size() - 1;
        double scaled = t * last;
        int segment = Math.min((int) Math.floor(scaled), last - 1);
        double local = scaled - segment;
        double[] p0 = points.get(Math.max(0, segment - 1));
        double[] p1 = points.get(segment);
        double[] p2 = points.get(segment + 1);
        double[] p3 = points.get(Math.min(last, segment + 2));
        return new double[] {
            catmull(p0[0], p1[0], p2[0], p3[0], local),
            catmull(p0[1], p1[1], p2[1], p3[1], local),
            catmull(p0[2], p1[2], p2[2], p3[2], local)
        };
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private static double[][] copy(List<double[]> points) {
        double[][] working = new double[points.size()][3];
        for (int i = 0; i < points.size(); i++) {
            requirePoint(points.get(i), "Curve control point #" + (i + 1));
            working[i][0] = points.get(i)[0];
            working[i][1] = points.get(i)[1];
            working[i][2] = points.get(i)[2];
        }
        return working;
    }

    private static void requirePoints(List<double[]> points, int minimum, String label) {
        if (points == null || points.size() < minimum) {
            throw new IllegalArgumentException(
                    label + " needs at least " + minimum + " points");
        }
    }

    private static void requirePoint(double[] point, String label) {
        if (point == null || point.length < POINT_COMPONENTS) {
            throw new IllegalArgumentException(label + " must be a Vector3-shaped table");
        }
        for (int i = 0; i < 3; i++) {
            if (!Double.isFinite(point[i])) {
                throw new IllegalArgumentException(label + " components must be finite numbers");
            }
        }
    }

    private static void requireT(double t) {
        if (!Double.isFinite(t) || t < 0.0 || t > 1.0) {
            throw new IllegalArgumentException("Curve t must be a finite number between 0 and 1");
        }
    }
}
