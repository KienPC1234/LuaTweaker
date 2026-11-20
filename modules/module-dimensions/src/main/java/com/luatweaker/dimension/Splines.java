package com.luatweaker.dimension;

import java.util.List;

/** Piecewise-linear spline evaluation shared by the height field and the density threshold. */
final class Splines {

    private Splines() {}

    /** Returns 0 for an empty spline; clamps outside the control points. */
    static double evaluate(List<TerrainSpec.SplinePoint> points, double input) {
        if (points.isEmpty()) return 0.0;
        TerrainSpec.SplinePoint first = points.get(0);
        if (input <= first.input()) return first.output();
        TerrainSpec.SplinePoint last = points.get(points.size() - 1);
        if (input >= last.input()) return last.output();
        for (int i = 1; i < points.size(); i++) {
            TerrainSpec.SplinePoint right = points.get(i);
            if (input > right.input()) continue;
            TerrainSpec.SplinePoint left = points.get(i - 1);
            double span = right.input() - left.input();
            double t = span == 0.0 ? 0.0 : (input - left.input()) / span;
            return left.output() + t * (right.output() - left.output());
        }
        return last.output();
    }
}
