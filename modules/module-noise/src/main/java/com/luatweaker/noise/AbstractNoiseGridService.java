package com.luatweaker.noise;

import com.luatweaker.api.noise.INoiseService;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Batch sampling half of {@link INoiseService}: 2D/3D field fills over a grid. Kept in a
 * separate collaborator so {@link NoiseServiceImpl} stays a thin scalar front end and the
 * single-source state snapshot lives in one place.
 */
abstract class AbstractNoiseGridService implements INoiseService {

    /** One immutable snapshot: setSeed from another thread must not mix two states in one field. */
    protected final AtomicReference<NoiseState> state = new AtomicReference<>();

    protected AbstractNoiseGridService(long seed) {
        state.set(NoiseState.create(seed));
    }

    @Override
    @NotNull
    public double[] fill2D(double x0, double z0, int width, int height, @NotNull ILuaValue spec,
                           int stepX, int stepZ) {
        NoiseValidation.validateGrid(width, height, 1);
        NoiseValidation.validateStep("stepX", stepX);
        NoiseValidation.validateStep("stepZ", stepZ);
        NoiseLayer cfg = NoiseSpecParser.parseSpec(spec, false);
        NoiseState snapshot = state.get();
        double[] out = new double[width * height];
        int index = 0;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                out[index++] = NoiseSampling.sample2D(snapshot, cfg, x0 + (double) x * stepX, z0 + (double) z * stepZ);
            }
        }
        return out;
    }

    @Override
    @NotNull
    public double[] fill2DSmooth(double x0, double z0, int width, int height, @NotNull ILuaValue spec,
                                 int stepX, int stepZ) {
        NoiseValidation.validateGrid(width, height, 1);
        NoiseValidation.validateStep("stepX", stepX);
        NoiseValidation.validateStep("stepZ", stepZ);
        if (width % stepX != 0) {
            throw new IllegalArgumentException("width (" + width + ") must be a multiple of stepX ("
                    + stepX + ") so the lattice edge lands on the next chunk's first column");
        }
        if (height % stepZ != 0) {
            throw new IllegalArgumentException("height (" + height + ") must be a multiple of stepZ ("
                    + stepZ + ") so the lattice edge lands on the next chunk's first column");
        }
        NoiseLayer cfg = NoiseSpecParser.parseSpec(spec, false);
        NoiseState snapshot = state.get();
        // Coarse lattice with one extra edge row/column at the far side: sampling the edge
        // at x0 + width lets the neighbouring chunk start from the identical value (no seam).
        int latticeCols = width / stepX + 1;
        int latticeRows = height / stepZ + 1;
        double[] lattice = new double[latticeCols * latticeRows];
        for (int j = 0; j < latticeRows; j++) {
            for (int i = 0; i < latticeCols; i++) {
                lattice[j * latticeCols + i] = NoiseSampling.sample2D(snapshot, cfg,
                        x0 + (double) i * stepX, z0 + (double) j * stepZ);
            }
        }
        double[] out = new double[width * height];
        for (int z = 0; z < height; z++) {
            int cellZ = z / stepZ;
            double fracZ = (double) (z % stepZ) / stepZ;
            for (int x = 0; x < width; x++) {
                int cellX = x / stepX;
                double fracX = (double) (x % stepX) / stepX;
                double topLeft = lattice[cellZ * latticeCols + cellX];
                double topRight = lattice[cellZ * latticeCols + cellX + 1];
                double bottomLeft = lattice[(cellZ + 1) * latticeCols + cellX];
                double bottomRight = lattice[(cellZ + 1) * latticeCols + cellX + 1];
                double top = topLeft + (topRight - topLeft) * fracX;
                double bottom = bottomLeft + (bottomRight - bottomLeft) * fracX;
                out[z * width + x] = top + (bottom - top) * fracZ;
            }
        }
        return out;
    }

    @Override
    @NotNull
    public double[] fill3D(double x0, double y0, double z0, int sizeX, int sizeY, int sizeZ,
                           @NotNull ILuaValue spec) {
        NoiseValidation.validateGrid(sizeX, sizeY, sizeZ);
        NoiseLayer cfg = NoiseSpecParser.parseSpec(spec, true);
        NoiseState snapshot = state.get();
        double[] out = new double[sizeX * sizeY * sizeZ];
        int index = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    out[index++] = NoiseSampling.sample3D(snapshot, cfg, x0 + x, y0 + y, z0 + z);
                }
            }
        }
        return out;
    }
}
