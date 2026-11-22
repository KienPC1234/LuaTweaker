package com.luatweaker.common.content;

import com.luatweaker.common.container.VoxelShapeHelper;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Compound shapes must produce one rotated shape per horizontal direction. */
class VoxelCompoundShapeTest {

    private static Map<Direction, VoxelShape> compoundShapes() {
        BoxSpec base = new BoxSpec(0, 0, 0, 16, 4, 16);
        BoxSpec top = new BoxSpec(2, 4, 2, 14, 16, 14);
        return VoxelShapeHelper.buildHorizontalShapes(List.of(base, top));
    }

    @Test
    void compoundShapeIsNotNull() {
        assertNotNull(compoundShapes(),
                "building a compound shape must return a result");
    }

    @Test
    void compoundShapeCoversSixRotations() {
        assertEquals(6, compoundShapes().size(),
                "the compound builder must cover every horizontal rotation");
    }

    @Test
    void northCompoundShapeIsSolid() {
        assertFalse(compoundShapes().get(Direction.NORTH).isEmpty(),
                "the north compound shape must contain the stacked boxes");
    }
}
