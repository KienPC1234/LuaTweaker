package com.luatweaker.common.content;

import com.luatweaker.common.container.VoxelShapeHelper;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A north-facing box rotated to west must mirror along x. */
class VoxelRotateWestTest {

    private static BoxSpec rotated() {
        // North z:[0..4] rotated 270 deg faces West -> x:[0..4]
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        return VoxelShapeHelper.rotateHorizontal(box, Direction.WEST);
    }

    @Test
    void minXBecomesNearSide() {
        assertEquals(0.0, rotated().minX(),
                "a west rotation must bring the west edge to zero");
    }

    @Test
    void maxXStaysNearSide() {
        assertEquals(4.0, rotated().maxX(),
                "a west rotation must keep the east edge on the near side");
    }

    @Test
    void minZBecomesNearSide() {
        assertEquals(0.0, rotated().minZ(),
                "a west rotation must bring the near edge to zero");
    }

    @Test
    void maxZSpansFullDepth() {
        assertEquals(16.0, rotated().maxZ(),
                "a west rotation must span the full depth");
    }
}
