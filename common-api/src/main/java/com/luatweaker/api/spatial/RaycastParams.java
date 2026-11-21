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
    /** Distance used when a caller passes a non-positive maximum. */
    public static final double DEFAULT_MAX_DISTANCE = 64.0;

    /**
     * Builds raycast parameters, clamping a non-positive distance to the
     * engine default (64 blocks).
     *
     * @return a validated parameter set
     */
    public static RaycastParams create(double maxDistance, boolean ignoreLiquids, boolean ignorePassableBlocks, @Nullable List<IEntity> ignoreEntities) {
        return new RaycastParams(maxDistance > 0 ? maxDistance : DEFAULT_MAX_DISTANCE, ignoreLiquids, ignorePassableBlocks, ignoreEntities);
    }
}
