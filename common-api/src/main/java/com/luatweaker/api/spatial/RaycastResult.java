package com.luatweaker.api.spatial;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.IWorldBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RaycastResult(
    boolean isHit,
    double hitX,
    double hitY,
    double hitZ,
    double normalX,
    double normalY,
    double normalZ,
    @NotNull String hitType,
    @Nullable IWorldBlock block,
    @Nullable IEntity entity,
    double distance
) {
    public static RaycastResult miss(double endX, double endY, double endZ) {
        return new RaycastResult(false, endX, endY, endZ, 0, 1, 0, "NONE", null, null, -1.0);
    }
}
