package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skylands must mix high islands with void columns between them. */
class SkylandsCoverageTest {

    private static final int SAMPLE_STEP = 4;
    private static final int SAMPLE_EXTENT = 256;
    private static final int ISLAND_MIN_HEIGHT = 180;
    private static final double MIN_ISLAND_FRACTION = 10.0;
    private static final int SURFACE_MARGIN = 1;
    private static final int SLAB_THICKNESS = 20;
    private static final String AIR_BLOCK = "minecraft:air";

    private record SlabSample(int top, int lowestSolid) {
    }

    private record Coverage(int islands, int voids, int total, List<Integer> islandHeights,
            List<SlabSample> slabs) {
    }

    private record ColumnOutcome(boolean island, int height, SlabSample slab) {
    }

    private static ColumnOutcome classifyColumn(DimensionServiceImpl service,
            DimensionConfig skylands, int x, int z) {
        DimensionServiceImpl.SurfaceResult surface =
                service.computeSurface(skylands.id(), x, z, skylands.seaLevel());
        if (surface.height() <= skylands.minHeight() + SURFACE_MARGIN) {
            return new ColumnOutcome(false, surface.height(), null);
        }
        // The Lua picker carves air below the island slab.
        Map<Integer, String> overrides = service.computeBlockOverrides(
                skylands.id(), x, z, surface.height(), skylands.minHeight(), 320);
        int lowestSolid = Integer.MAX_VALUE;
        for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
            if (!AIR_BLOCK.equals(entry.getValue())) {
                lowestSolid = Math.min(lowestSolid, entry.getKey());
            }
        }
        return new ColumnOutcome(true, surface.height(),
                new SlabSample(surface.height(), lowestSolid));
    }

    private static Coverage scanCoverage() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig skylands = DimensionShowcaseFixture.getSkylands();
        int islands = 0;
        int voids = 0;
        int total = 0;
        List<Integer> islandHeights = new ArrayList<>();
        List<SlabSample> slabs = new ArrayList<>();
        for (int z = -SAMPLE_EXTENT; z <= SAMPLE_EXTENT; z += SAMPLE_STEP) {
            for (int x = -SAMPLE_EXTENT; x <= SAMPLE_EXTENT; x += SAMPLE_STEP) {
                total++;
                ColumnOutcome outcome = classifyColumn(service, skylands, x, z);
                if (outcome.island()) {
                    islands++;
                    islandHeights.add(outcome.height());
                    slabs.add(outcome.slab());
                } else {
                    voids++;
                }
            }
        }
        return new Coverage(islands, voids, total, islandHeights, slabs);
    }

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    @Test
    void islandTopsStayAtSkylandAltitude() {
        Coverage coverage = scanCoverage();
        for (int height : coverage.islandHeights()) {
            assertTrue(height >= ISLAND_MIN_HEIGHT,
                    "island too low: " + height);
        }
    }

    @Test
    void islandSlabsStayThin() {
        Coverage coverage = scanCoverage();
        for (SlabSample slab : coverage.slabs()) {
            assertTrue(slab.lowestSolid() >= slab.top() - SLAB_THICKNESS,
                    "island slab must be thin: top=" + slab.top()
                            + " lowestSolid=" + slab.lowestSolid());
        }
    }

    @Test
    void islandsCoverTheWorld() {
        Coverage coverage = scanCoverage();

        assertTrue(coverage.islands() * 100.0 / coverage.total() > MIN_ISLAND_FRACTION,
                "skylands must have islands, got " + coverage.islands() + "/" + coverage.total());
    }

    @Test
    void voidExistsBetweenIslands() {
        assertTrue(scanCoverage().voids() > 0,
                "skylands must have void between islands");
    }
}
