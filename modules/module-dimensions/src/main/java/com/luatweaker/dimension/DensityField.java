package com.luatweaker.dimension;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One chunk's 3D density field: noise is sampled on a coarse cell grid and interpolated
 * trilinearly per block, the same trade-off vanilla uses (density noise is low-frequency, so
 * interpolating between cells is visually indistinguishable and far cheaper).
 *
 * <p>Solidity is {@code interpolatedDensity > thresholdByY[y - minY]}, where the per-Y
 * threshold folds in the spec's threshold spline. Instances are immutable.</p>
 */
public final class DensityField {

    private final double[] values;
    private final double[] thresholdByY;
    private final int cellsX;
    private final int cellsY;
    private final int cellSize;
    private final int cellHeight;
    private final int baseX;
    private final int baseZ;
    private final int minY;
    private final int maxY;
    private final List<TerrainSpec.Platform> platforms;

    DensityField(@NotNull double[] values, int cellsX, int cellsY, int cellSize, int cellHeight,
                 int baseX, int baseZ, int minY, int maxY, @NotNull double[] thresholdByY,
                 @NotNull List<TerrainSpec.Platform> platforms) {
        this.values = values;
        this.cellsX = cellsX;
        this.cellsY = cellsY;
        this.cellSize = cellSize;
        this.cellHeight = cellHeight;
        this.baseX = baseX;
        this.baseZ = baseZ;
        this.minY = minY;
        this.maxY = maxY;
        this.thresholdByY = thresholdByY;
        this.platforms = platforms;
    }

    /** Default cell size when a spec does not set one. */
    public static final int DEFAULT_CELL_SIZE = 4;
    /** Default vertical cell height when a spec does not set one. */
    public static final int DEFAULT_CELL_HEIGHT = 4;

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    /** True when the block at chunk-local (x, y, z) is solid. Out-of-range Y is never solid. */
    public boolean isSolid(int localX, int y, int localZ) {
        if (y < minY || y >= maxY) return false;
        if (isPlatformSolid(baseX + localX, y, baseZ + localZ)) return true;
        return interpolate(localX, y, localZ) > thresholdByY[y - minY];
    }

    /** Platforms are forced solid cylinders, independent of the noise threshold. */
    private boolean isPlatformSolid(int worldX, int y, int worldZ) {
        for (TerrainSpec.Platform platform : platforms) {
            if (y < platform.bottomY() || y > platform.topY()) continue;
            long dx = (long) worldX - platform.x();
            long dz = (long) worldZ - platform.z();
            if (dx * dx + dz * dz <= (long) platform.radius() * platform.radius()) {
                return true;
            }
        }
        return false;
    }

    /** Highest solid Y of a column, or {@code minY - 1} when the column is empty. */
    public int topSolid(int localX, int localZ) {
        for (int y = maxY - 1; y >= minY; y--) {
            if (isSolid(localX, y, localZ)) return y;
        }
        return minY - 1;
    }

    private double interpolate(int localX, int y, int localZ) {
        int gx = localX / cellSize;
        double fx = (localX % cellSize) / (double) cellSize;
        int gz = localZ / cellSize;
        double fz = (localZ % cellSize) / (double) cellSize;
        int gy = (y - minY) / cellHeight;
        double fy = ((y - minY) % cellHeight) / (double) cellHeight;

        int nextX = Math.min(gx + 1, cellsX - 1);
        int nextY = Math.min(gy + 1, cellsY - 1);
        // cellsX == cellsZ for a square chunk.
        int nextZ = Math.min(gz + 1, cellsX - 1);

        double c000 = at(gx, gy, gz);
        double c100 = at(nextX, gy, gz);
        double c010 = at(gx, nextY, gz);
        double c110 = at(nextX, nextY, gz);
        double c001 = at(gx, gy, nextZ);
        double c101 = at(nextX, gy, nextZ);
        double c011 = at(gx, nextY, nextZ);
        double c111 = at(nextX, nextY, nextZ);

        double x00 = lerp(c000, c100, fx);
        double x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx);
        double x11 = lerp(c011, c111, fx);
        double y0 = lerp(x00, x10, fy);
        double y1 = lerp(x01, x11, fy);
        return lerp(y0, y1, fz);
    }

    private double at(int gx, int gy, int gz) {
        return values[(gy * cellsX + gz) * cellsX + gx];
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
