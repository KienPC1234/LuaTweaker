package com.luatweaker.common.content;

import com.luatweaker.common.container.VoxelShapeHelper;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A north-facing box rotated to south must mirror along z. */
class VoxelRotateSouthTest {

    private static BoxSpec rotated() {
        // North z:[0..4] rotated 180 to South becomes z:[12..16]
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        return VoxelShapeHelper.rotateHorizontal(box, Direction.SOUTH);
    }

    @Test
    void minXIsKept() {
        assertEquals(0.0, rotated().minX(),
                "a south rotation must keep the west edge");
    }

    @Test
    void maxXIsKept() {
        assertEquals(16.0, rotated().maxX(),
                "a south rotation must keep the east edge");
    }

    @Test
    void minZMovesToFarSide() {
        assertEquals(12.0, rotated().minZ(),
                "a south rotation must move the near edge to the far side");
    }

    @Test
    void maxZMovesToFarSide() {
        assertEquals(16.0, rotated().maxZ(),
                "a south rotation must move the far edge out to the boundary");
    }
}
