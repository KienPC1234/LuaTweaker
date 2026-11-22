package com.luatweaker.common.dimension;

import com.luatweaker.dimension.TerrainColumn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Void columns must not be clamped onto the bottom layer (that made them look solid). */
class TerrainClampTest {

    @Test
    void belowFloorBecomesVoidColumn() {
        assertEquals(-65, TerrainColumn.clampHeight(-64, -64, 320),
                "one below the floor must clamp to the void marker");
    }

    @Test
    void farBelowFloorBecomesVoidColumn() {
        assertEquals(-65, TerrainColumn.clampHeight(-500, -64, 320),
                "deep below the floor must clamp to the void marker");
    }

    @Test
    void floorEdgeStaysGround() {
        assertEquals(-63, TerrainColumn.clampHeight(-63, -64, 320),
                "one above the floor must stay inside the build height");
    }

    @Test
    void midHeightIsKept() {
        assertEquals(180, TerrainColumn.clampHeight(180, -64, 320),
                "a mid-world height must pass through unchanged");
    }

    @Test
    void aboveTopClampsToBuildHeight() {
        assertEquals(319, TerrainColumn.clampHeight(9_999, -64, 320),
                "heights past the top must clamp to the build limit");
    }
}
