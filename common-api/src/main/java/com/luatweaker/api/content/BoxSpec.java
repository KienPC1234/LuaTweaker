package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Bounding box specification in 0-16 voxel coordinates.")
public record BoxSpec(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public BoxSpec {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(String.format(
                    "Invalid BoxSpec bounds: min(%.1f, %.1f, %.1f) > max(%.1f, %.1f, %.1f)",
                    minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}
