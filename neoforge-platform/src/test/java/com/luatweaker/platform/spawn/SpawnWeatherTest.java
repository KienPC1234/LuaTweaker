package com.luatweaker.platform.spawn;

import com.luatweaker.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spawn gating that runs for every natural spawn attempt: weather. */
class SpawnWeatherTest {

    @Test
    void rainOnlyAllowsRain() {
        SpawnRule rainOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, Boolean.TRUE, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(rainOnly, 0, 0, 0, true, true, false),
                "rain must satisfy a rain-only rule");
    }

    @Test
    void rainOnlyRejectsClearSkies() {
        SpawnRule rainOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, Boolean.TRUE, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(rainOnly, 0, 0, 0, true, false, false),
                "clear skies must not satisfy a rain-only rule");
    }

    @Test
    void thunderOnlyAllowsThunder() {
        SpawnRule thunderOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, null, Boolean.TRUE);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(thunderOnly, 0, 0, 0, true, true, true),
                "thunder must satisfy a thunder-only rule");
    }

    @Test
    void thunderOnlyRejectsPlainRain() {
        SpawnRule thunderOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, null, Boolean.TRUE);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(thunderOnly, 0, 0, 0, true, true, false),
                "rain without thunder must not satisfy a thunder-only rule");
    }

    @Test
    void clearOnlyAllowsClearSkies() {
        SpawnRule clearOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, Boolean.FALSE, Boolean.FALSE);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(clearOnly, 0, 0, 0, true, false, false),
                "clear skies must satisfy a clear-only rule");
    }

    @Test
    void clearOnlyRejectsRain() {
        SpawnRule clearOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, Boolean.FALSE, Boolean.FALSE);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(clearOnly, 0, 0, 0, true, true, false),
                "rain must not satisfy a clear-only rule");
    }

    @Test
    void noFlagsAllowDayRainAndThunder() {
        SpawnRule any = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(any, 0, 0, 0, true, false, false),
                "a flagless rule must allow calm day spawns");
    }

    @Test
    void noFlagsAllowStormyNight() {
        SpawnRule any = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(any, 0, 15, 15, false, true, true),
                "a flagless rule must allow stormy night spawns");
    }
}
