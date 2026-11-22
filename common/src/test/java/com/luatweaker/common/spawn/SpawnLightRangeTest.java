package com.luatweaker.common.spawn;

import com.luatweaker.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spawn gating that runs for every natural spawn attempt: light ranges. */
class SpawnLightRangeTest {

    @Test
    void blockLightMinimumAllowsDark() {
        SpawnRule dark = SpawnRuleFixtures.rule(null, null, 0, 7, null, null, null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(dark, 0, 0, 0, true, false, false),
                "total darkness must satisfy a 0-7 block-light window");
    }

    @Test
    void blockLightMaximumIsInclusive() {
        SpawnRule dark = SpawnRuleFixtures.rule(null, null, 0, 7, null, null, null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(dark, 0, 7, 15, true, false, false),
                "the maximum block light must still allow spawning");
    }

    @Test
    void blockLightAboveMaximumIsRejected() {
        SpawnRule dark = SpawnRuleFixtures.rule(null, null, 0, 7, null, null, null, null, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(dark, 0, 8, 0, true, false, false),
                "block light above the window must reject spawning");
    }

    @Test
    void skyLightInsideWindowAllows() {
        SpawnRule shaded = SpawnRuleFixtures.rule(null, null, null, null, 0, 4, null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(shaded, 0, 15, 4, true, false, false),
                "sky light at the window edge must still allow spawning");
    }

    @Test
    void skyLightAboveWindowIsRejected() {
        SpawnRule shaded = SpawnRuleFixtures.rule(null, null, null, null, 0, 4, null, null, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(shaded, 0, 0, 5, true, false, false),
                "sky light above the window must reject spawning");
    }
}
