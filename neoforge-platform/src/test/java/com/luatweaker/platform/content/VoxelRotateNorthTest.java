package com.luatweaker.platform.content;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A north-facing box rotated to north must keep its coordinates. */
class VoxelRotateNorthTest {

    private static BoxSpec rotated() {
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        return VoxelShapeHelper.rotateHorizontal(box, Direction.NORTH);
    }

    @Test
    void minXIsKept() {
        assertEquals(0.0, rotated().minX(),
                "a north rotation must keep the west edge");
    }

    @Test
    void maxXIsKept() {
        assertEquals(16.0, rotated().maxX(),
                "a north rotation must keep the east edge");
    }

    @Test
    void minZIsKept() {
        assertEquals(0.0, rotated().minZ(),
                "a north rotation must keep the north edge");
    }

    @Test
    void maxZIsKept() {
        assertEquals(4.0, rotated().maxZ(),
                "a north rotation must keep the south edge");
    }
}
