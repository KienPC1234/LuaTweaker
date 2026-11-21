package com.luatweaker.api.content;

import org.jetbrains.annotations.NotNull;

/**
 * A single item drop entry for a custom entity: item id plus an inclusive
 * random count range. Builders accumulate these; the platform spawns one
 * stack per entry when the entity dies.
 *
 * @param itemId   item id (e.g. {@code "luatweaker:crystal_shard"})
 * @param minCount minimum stack size (at least 1)
 * @param maxCount maximum stack size (at least minCount)
 */
public record EntityDrop(@NotNull String itemId, int minCount, int maxCount) {
    /** Smallest legal stack size for a drop entry. */
    private static final int MIN_COUNT = 1;

    public EntityDrop {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("drop itemId must not be blank");
        }
        if (minCount < MIN_COUNT) {
            throw new IllegalArgumentException("drop minCount must be at least 1");
        }
        if (maxCount < minCount) {
            throw new IllegalArgumentException("drop maxCount must be >= minCount");
        }
    }
}
