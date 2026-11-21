package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 512x512 area around spawn must be almost entirely solid terrain in bounds. */
class CrystalRealmCoverageTest {

    private static final int SAMPLE_STEP = 8;
    private static final int SAMPLE_EXTENT = 256;
    private static final double MIN_SOLID_FRACTION = 95.0;
    private static final int SURFACE_MARGIN = 1;

    private record Coverage(int solidColumns, int totalColumns, List<Integer> solidHeights) {
    }

    private static Coverage scanCoverage() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig realm = DimensionShowcaseFixture.getRealm();
        int solid = 0;
        int total = 0;
        List<Integer> heights = new ArrayList<>();
        for (int z = -SAMPLE_EXTENT; z <= SAMPLE_EXTENT; z += SAMPLE_STEP) {
            for (int x = -SAMPLE_EXTENT; x <= SAMPLE_EXTENT; x += SAMPLE_STEP) {
                total++;
                DimensionServiceImpl.SurfaceResult surface =
                        service.computeSurface(realm.id(), x, z, realm.seaLevel());
                if (surface.height() > realm.minHeight() + SURFACE_MARGIN) {
                    solid++;
                    heights.add(surface.height());
                }
            }
        }
        return new Coverage(solid, total, heights);
    }

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    @Test
    void solidColumnsStayInWorldBounds() {
        DimensionConfig realm = DimensionShowcaseFixture.getRealm();
        Coverage coverage = scanCoverage();
        for (int height : coverage.solidHeights()) {
            assertTrue(height >= realm.minHeight() + SURFACE_MARGIN && height < realm.maxHeight(),
                    "height out of bounds: " + height);
        }
    }

    @Test
    void worldIsMostlySolidTerrain() {
        Coverage coverage = scanCoverage();

        assertTrue(coverage.solidColumns() * 100.0 / coverage.totalColumns() > MIN_SOLID_FRACTION,
                "realm must be mostly solid terrain, got " + coverage.solidColumns()
                        + "/" + coverage.totalColumns() + " solid columns");
    }
}
