package com.luatweaker.platform.content;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A north-facing box rotated to east must swap the x and z extents. */
class VoxelRotateEastTest {

    private static BoxSpec rotated() {
        // North z:[0..4] rotated 90 deg clockwise faces East -> x:[12..16]
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        return VoxelShapeHelper.rotateHorizontal(box, Direction.EAST);
    }

    @Test
    void minXMovesToFarSide() {
        assertEquals(12.0, rotated().minX(),
                "an east rotation must move the west edge to the far side");
    }

    @Test
    void maxXMovesToFarSide() {
        assertEquals(16.0, rotated().maxX(),
                "an east rotation must move the east edge out to the boundary");
    }

    @Test
    void minZBecomesNearSide() {
        assertEquals(0.0, rotated().minZ(),
                "an east rotation must bring the near edge to zero");
    }

    @Test
    void maxZSpansFullDepth() {
        assertEquals(16.0, rotated().maxZ(),
                "an east rotation must span the full depth");
    }
}
