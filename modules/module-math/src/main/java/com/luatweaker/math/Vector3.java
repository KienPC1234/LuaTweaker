package com.luatweaker.math;

public record Vector3(double x, double y, double z) {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 ONE = new Vector3(1, 1, 1);

    public double getMagnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3 getUnit() {
        double m = getMagnitude();
        if (m == 0) return ZERO;
        return new Vector3(x / m, y / m, z / m);
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 sub(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 mul(double scale) {
        return new Vector3(x * scale, y * scale, z * scale);
    }

    public Vector3 div(double scale) {
        if (scale == 0) return ZERO;
        return new Vector3(x / scale, y / scale, z / scale);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public Vector3 lerp(Vector3 other, double alpha) {
        return new Vector3(
            x + (other.x - x) * alpha,
            y + (other.y - y) * alpha,
            z + (other.z - z) * alpha
        );
    }

    public double angle(Vector3 other) {
        double m1 = getMagnitude();
        double m2 = other.getMagnitude();
        if (m1 == 0 || m2 == 0) return 0;
        double cos = dot(other) / (m1 * m2);
        return Math.acos(Math.clamp(cos, -1.0, 1.0));
    }
}
