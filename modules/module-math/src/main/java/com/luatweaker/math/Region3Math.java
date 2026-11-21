package com.luatweaker.math;

/**
 * Pure axis-aligned box math behind the Lua {@code Region3} table: point containment,
 * box intersection, union, expansion and volume. No engine types, so it is unit-testable.
 */
final class Region3Math {

    private static final String NON_FINITE = "Region3 coordinates must be finite numbers";

    private Region3Math() {
    }

    /** A validated point; every component must be finite. */
    record Point(double x, double y, double z) {
        Point {
            requireFinite(x, y, z);
        }
    }

    /** Immutable axis-aligned box; the factory normalises so min &lt;= max always holds. */
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        boolean intersects(Box other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        double volume() {
            return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
        }

        Box expand(double amount) {
            requireFinite(amount);
            return new Box(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount);
        }

        Box union(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }

        Point center() {
            return new Point((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        }

        Point size() {
            return new Point(maxX - minX, maxY - minY, maxZ - minZ);
        }
    }

    static Box create(Point first, Point second) {
        return new Box(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()), Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
    }

    static Box create(double x1, double y1, double z1, double x2, double y2, double z2) {
        return create(new Point(x1, y1, z1), new Point(x2, y2, z2));
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(NON_FINITE);
            }
        }
    }
}
