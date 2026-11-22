package com.luatweaker.common.spawn;

import com.luatweaker.spawn.SpawnRule;

import java.util.List;

/** Shared spawn-rule builder for the gating tests. */
final class SpawnRuleFixtures {

    private SpawnRuleFixtures() {
    }

    static SpawnRule rule(Integer minY, Integer maxY, Integer minLight, Integer maxLight,
            Integer minSky, Integer maxSky,
            Boolean day, Boolean rain, Boolean thunder) {
        return new SpawnRule("minecraft:zombie", null, 10, 1, 1,
                minY == null ? -2032 : minY,
                maxY == null ? 2032 : maxY,
                minLight == null ? 0 : minLight,
                maxLight == null ? 15 : maxLight,
                minSky == null ? 0 : minSky,
                maxSky == null ? 15 : maxSky,
                day, rain, thunder, List.of(), List.of(), false, null);
    }
}
