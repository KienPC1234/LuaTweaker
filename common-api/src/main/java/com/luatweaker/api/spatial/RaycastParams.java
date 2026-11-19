package com.luatweaker.api.spatial;

import com.luatweaker.api.entity.IEntity;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public record RaycastParams(
    double maxDistance,
    boolean ignoreLiquids,
    boolean ignorePassableBlocks,
    @Nullable List<IEntity> ignoreEntities
) {
    public static RaycastParams of(double maxDistance, boolean ignoreLiquids, boolean ignorePassableBlocks, @Nullable List<IEntity> ignoreEntities) {
        return new RaycastParams(maxDistance > 0 ? maxDistance : 64.0, ignoreLiquids, ignorePassableBlocks, ignoreEntities);
    }
}
