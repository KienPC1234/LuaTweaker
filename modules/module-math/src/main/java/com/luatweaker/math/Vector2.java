package com.luatweaker.math;

public record Vector2(double x, double y) {
    public static final Vector2 ZERO = new Vector2(0, 0);
    public static final Vector2 ONE = new Vector2(1, 1);

    public double getMagnitude() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2 getUnit() {
        double m = getMagnitude();
        if (m == 0) return ZERO;
        return new Vector2(x / m, y / m);
    }

    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 sub(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 mul(double scale) {
        return new Vector2(x * scale, y * scale);
    }

    public Vector2 div(double scale) {
        if (scale == 0) return ZERO;
        return new Vector2(x / scale, y / scale);
    }

    public double dot(Vector2 other) {
        return x * other.x + y * other.y;
    }

    public Vector2 lerp(Vector2 other, double alpha) {
        return new Vector2(
            x + (other.x - x) * alpha,
            y + (other.y - y) * alpha
        );
    }
}
