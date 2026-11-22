package com.luatweaker.common.dimension;

import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terrain queries must stay deterministic and lakes must stay inside the world. */
class RealmTerrainSanityTest {

    private static final int SAMPLE_EXTENT = 64;
    private static final int SAMPLE_STEP = 2;
    private static final int PROBE_X = 123;
    private static final int PROBE_Z = -456;
    private static final int SURFACE_MARGIN = 1;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
    }

    @Test
    void lakeBasinsStayInsideWorldBounds() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig realm = DimensionShowcaseFixture.getRealm();
        // Water lakes are made in Lua by lowering the surface below sea level
        // so the engine's water fill creates the lake surface.
        for (int z = -SAMPLE_EXTENT; z <= SAMPLE_EXTENT; z += SAMPLE_STEP) {
            for (int x = -SAMPLE_EXTENT; x <= SAMPLE_EXTENT; x += SAMPLE_STEP) {
                DimensionServiceImpl.SurfaceResult surface =
                        service.computeSurface(realm.id(), x, z, realm.seaLevel());
                if (surface.height() < realm.seaLevel()) {
                    assertTrue(surface.height() >= realm.minHeight() + SURFACE_MARGIN,
                            "lake basin must stay inside world bounds at (" + x + "," + z + ")");
                }
            }
        }
    }

    @Test
    void terrainHeightIsDeterministic() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig realm = DimensionShowcaseFixture.getRealm();
        DimensionServiceImpl.SurfaceResult first =
                service.computeSurface(realm.id(), PROBE_X, PROBE_Z, realm.seaLevel());
        DimensionServiceImpl.SurfaceResult second =
                service.computeSurface(realm.id(), PROBE_X, PROBE_Z, realm.seaLevel());

        assertEquals(first.height(), second.height(),
                "the same column must report the same height on every call");
    }

    @Test
    void terrainBlockIsDeterministic() {
        DimensionServiceImpl service = DimensionShowcaseFixture.getService();
        DimensionConfig realm = DimensionShowcaseFixture.getRealm();
        DimensionServiceImpl.SurfaceResult first =
                service.computeSurface(realm.id(), PROBE_X, PROBE_Z, realm.seaLevel());
        DimensionServiceImpl.SurfaceResult second =
                service.computeSurface(realm.id(), PROBE_X, PROBE_Z, realm.seaLevel());

        assertEquals(first.blockId(), second.blockId(),
                "the same column must report the same block on every call");
    }
}
