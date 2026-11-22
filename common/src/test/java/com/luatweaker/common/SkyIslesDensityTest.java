package com.luatweaker.common;

import com.luatweaker.dimension.DensityField;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.dimension.TerrainSampler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sky_isles density field must mix islands with void. */
class SkyIslesDensityTest {

    private static final int CHUNK_START = 8;
    private static final int CHUNK_END = 10;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int HEIGHT_SPREAD_MIN = 10;
    private static final double MIN_VOID_FRACTION = 0.45;
    private static final double MAX_VOID_FRACTION = 0.92;

    private record DensitySample(int minTop, int maxTop, double voidFraction) {
    }

    private record ChunkSample(int minTop, int maxTop, int voidColumns, int skyColumns) {
    }

    private static ChunkSample sampleChunk(TerrainSampler skySampler, int chunkX, int chunkZ) {
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int voidColumns = 0;
        int skyColumns = 0;
        DensityField field = skySampler.sampleDensity(chunkX, chunkZ, MIN_Y, MAX_Y);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = field.topSolid(x, z);
                if (top < MIN_Y) {
                    voidColumns++;
                } else {
                    minTop = Math.min(minTop, top);
                    maxTop = Math.max(maxTop, top);
                }
                skyColumns++;
            }
        }
        return new ChunkSample(minTop, maxTop, voidColumns, skyColumns);
    }

    private static DensitySample sampleDensity() {
        DimensionServiceImpl dimensions = AutonomousModsFixture.getDimensions();
        TerrainSampler skySampler = dimensions.createTerrainSampler(AutonomousModsFixture.SKY_ISLES);

        assertNotNull(skySampler,
                "the sky density sampler must exist");
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int voidColumns = 0;
        int skyColumns = 0;
        for (int chunkX = CHUNK_START; chunkX <= CHUNK_END; chunkX++) {
            for (int chunkZ = CHUNK_START; chunkZ <= CHUNK_END; chunkZ++) {
                ChunkSample chunk = sampleChunk(skySampler, chunkX, chunkZ);
                minTop = Math.min(minTop, chunk.minTop());
                maxTop = Math.max(maxTop, chunk.maxTop());
                voidColumns += chunk.voidColumns();
                skyColumns += chunk.skyColumns();
            }
        }
        return new DensitySample(minTop, maxTop, (double) voidColumns / skyColumns);
    }

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
    }

    @Test
    void densityIsNotFlat() {
        DensitySample sample = sampleDensity();

        assertTrue(sample.maxTop() - sample.minTop() > HEIGHT_SPREAD_MIN,
                "sky_isles density is flat: " + sample.minTop() + ".." + sample.maxTop());
    }

    @Test
    void voidFractionIsAnIslandField() {
        DensitySample sample = sampleDensity();

        assertTrue(sample.voidFraction() > MIN_VOID_FRACTION
                        && sample.voidFraction() < MAX_VOID_FRACTION,
                "sky_isles is not an island field, void fraction=" + sample.voidFraction());
    }
}
