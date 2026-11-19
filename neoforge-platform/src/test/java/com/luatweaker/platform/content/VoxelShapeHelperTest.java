package com.luatweaker.platform.content;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VoxelShapeHelperTest {

    @Test
    public void testRotateHorizontalNorth() {
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        BoxSpec rotated = VoxelShapeHelper.rotateHorizontal(box, Direction.NORTH);
        assertEquals(0.0, rotated.minX());
        assertEquals(16.0, rotated.maxX());
        assertEquals(0.0, rotated.minZ());
        assertEquals(4.0, rotated.maxZ());
    }

    @Test
    public void testRotateHorizontalSouth() {
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        BoxSpec rotated = VoxelShapeHelper.rotateHorizontal(box, Direction.SOUTH);
        // North z:[0..4] rotated 180 to South becomes z:[12..16]
        assertEquals(0.0, rotated.minX());
        assertEquals(16.0, rotated.maxX());
        assertEquals(12.0, rotated.minZ());
        assertEquals(16.0, rotated.maxZ());
    }

    @Test
    public void testRotateHorizontalEast() {
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        BoxSpec rotated = VoxelShapeHelper.rotateHorizontal(box, Direction.EAST);
        // North z:[0..4] rotated 90 deg clockwise faces East -> x:[12..16]
        assertEquals(12.0, rotated.minX());
        assertEquals(16.0, rotated.maxX());
        assertEquals(0.0, rotated.minZ());
        assertEquals(16.0, rotated.maxZ());
    }

    @Test
    public void testRotateHorizontalWest() {
        BoxSpec box = new BoxSpec(0, 0, 0, 16, 8, 4);
        BoxSpec rotated = VoxelShapeHelper.rotateHorizontal(box, Direction.WEST);
        // North z:[0..4] rotated 270 deg faces West -> x:[0..4]
        assertEquals(0.0, rotated.minX());
        assertEquals(4.0, rotated.maxX());
        assertEquals(0.0, rotated.minZ());
        assertEquals(16.0, rotated.maxZ());
    }

    @Test
    public void testCompoundShapeSpec() {
        BoxSpec base = new BoxSpec(0, 0, 0, 16, 4, 16);
        BoxSpec top = new BoxSpec(2, 4, 2, 14, 16, 14);
        var shapes = VoxelShapeHelper.buildHorizontalShapes(java.util.List.of(base, top));
        assertNotNull(shapes);
        assertEquals(6, shapes.size());
        assertFalse(shapes.get(Direction.NORTH).isEmpty());
    }
}
