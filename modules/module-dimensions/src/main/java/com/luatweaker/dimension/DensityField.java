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

    /** Cell-grid geometry of one chunk's density field. */
    public record Grid(int cellsX, int cellsY, int cellSize, int cellHeight, int baseX, int baseZ) {}

    private final double[] values;
    private final double[] thresholdByY;
    private final Grid grid;
    private final int yMin;
    private final int yMax;
    private final List<TerrainSpec.Platform> platforms;

    DensityField(@NotNull double[] values, @NotNull Grid grid, int minY, int maxY,
                 @NotNull double[] thresholdByY, @NotNull List<TerrainSpec.Platform> platforms) {
        this.values = values.clone();
        this.grid = grid;
        this.yMin = minY;
        this.yMax = maxY;
        this.thresholdByY = thresholdByY.clone();
        this.platforms = platforms;
    }

    /** Default cell size when a spec does not set one. */
    public static final int DEFAULT_CELL_SIZE = 4;
    /** Default vertical cell height when a spec does not set one. */
    public static final int DEFAULT_CELL_HEIGHT = 4;

    public int minY() {
        return yMin;
    }

    public int maxY() {
        return yMax;
    }

    /** True when the block at chunk-local (x, y, z) is solid. Out-of-range Y is never solid. */
    public boolean isSolid(int localX, int y, int localZ) {
        if (y < yMin || y >= yMax) {
            return false;
        }
        return isPlatformSolid(grid.baseX() + localX, y, grid.baseZ() + localZ)
                || interpolate(localX, y, localZ) > thresholdByY[y - yMin];
    }

    /** Platforms are forced solid cylinders, independent of the noise threshold. */
    private boolean isPlatformSolid(int worldX, int y, int worldZ) {
        for (TerrainSpec.Platform platform : platforms) {
            if (y < platform.bottomY() || y > platform.topY()) {
                continue;
            }
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
        for (int y = yMax - 1; y >= yMin; y--) {
            if (isSolid(localX, y, localZ)) {
                return y;
            }
        }
        return yMin - 1;
    }

    private double interpolate(int localX, int y, int localZ) {
        int gx = localX / grid.cellSize();
        double fx = localX % grid.cellSize() / (double) grid.cellSize();
        int gz = localZ / grid.cellSize();
        double fz = localZ % grid.cellSize() / (double) grid.cellSize();
        int gy = (y - yMin) / grid.cellHeight();
        double fy = (y - yMin) % grid.cellHeight() / (double) grid.cellHeight();

        int nextX = Math.min(gx + 1, grid.cellsX() - 1);
        int nextY = Math.min(gy + 1, grid.cellsY() - 1);
        // cellsX == cellsZ for a square chunk.
        int nextZ = Math.min(gz + 1, grid.cellsX() - 1);

        double c000 = cellValue(gx, gy, gz);
        double c100 = cellValue(nextX, gy, gz);
        double c010 = cellValue(gx, nextY, gz);
        double c110 = cellValue(nextX, nextY, gz);
        double c001 = cellValue(gx, gy, nextZ);
        double c101 = cellValue(nextX, gy, nextZ);
        double c011 = cellValue(gx, nextY, nextZ);
        double c111 = cellValue(nextX, nextY, nextZ);

        double x00 = lerp(c000, c100, fx);
        double x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx);
        double x11 = lerp(c011, c111, fx);
        double y0 = lerp(x00, x10, fy);
        double y1 = lerp(x01, x11, fy);
        return lerp(y0, y1, fz);
    }

    private double cellValue(int gx, int gy, int gz) {
        return values[(gy * grid.cellsX() + gz) * grid.cellsX() + gx];
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
