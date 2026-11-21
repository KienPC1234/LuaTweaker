package com.luatweaker.platform.content;

import com.luatweaker.api.content.BoxSpec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class VoxelShapeHelper {
    private VoxelShapeHelper() {}

    public static VoxelShape buildShape(List<BoxSpec> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return Shapes.block();
        }
        VoxelShape result = Shapes.empty();
        for (BoxSpec b : boxes) {
            VoxelShape box = Block.box(
                    Math.clamp(b.minX(), 0.0, 16.0),
                    Math.clamp(b.minY(), 0.0, 16.0),
                    Math.clamp(b.minZ(), 0.0, 16.0),
                    Math.clamp(b.maxX(), 0.0, 16.0),
                    Math.clamp(b.maxY(), 0.0, 16.0),
                    Math.clamp(b.maxZ(), 0.0, 16.0)
            );
            result = Shapes.join(result, box, BooleanOp.OR);
        }
        return result.optimize();
    }

    public static Map<Direction, VoxelShape> buildHorizontalShapes(List<BoxSpec> boxes) {
        var map = new EnumMap<Direction, VoxelShape>(Direction.class);
        if (boxes == null || boxes.isEmpty()) {
            VoxelShape full = Shapes.block();
            for (Direction d : Direction.values()) {
                map.put(d, full);
            }
            return map;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (BoxSpec b : boxes) {
                BoxSpec rot = rotateHorizontal(b, dir);
                VoxelShape box = Block.box(
                        Math.clamp(rot.minX(), 0.0, 16.0),
                        Math.clamp(rot.minY(), 0.0, 16.0),
                        Math.clamp(rot.minZ(), 0.0, 16.0),
                        Math.clamp(rot.maxX(), 0.0, 16.0),
                        Math.clamp(rot.maxY(), 0.0, 16.0),
                        Math.clamp(rot.maxZ(), 0.0, 16.0)
                );
                shape = Shapes.join(shape, box, BooleanOp.OR);
            }
            map.put(dir, shape.optimize());
        }
        VoxelShape north = map.get(Direction.NORTH);
        map.put(Direction.UP, north);
        map.put(Direction.DOWN, north);
        return map;
    }

    public static BoxSpec rotateHorizontal(BoxSpec b, Direction dir) {
        return switch (dir) {
            case NORTH -> b;
            case SOUTH -> new BoxSpec(16.0 - b.maxX(), b.minY(), 16.0 - b.maxZ(),
                                      16.0 - b.minX(), b.maxY(), 16.0 - b.minZ());
            case WEST -> new BoxSpec(b.minZ(), b.minY(), 16.0 - b.maxX(),
                                     b.maxZ(), b.maxY(), 16.0 - b.minX());
            case EAST -> new BoxSpec(16.0 - b.maxZ(), b.minY(), b.minX(),
                                     16.0 - b.minZ(), b.maxY(), b.maxX());
            default -> b;
        };
    }
}
