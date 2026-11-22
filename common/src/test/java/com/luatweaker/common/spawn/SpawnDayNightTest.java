package com.luatweaker.common.spawn;

import com.luatweaker.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spawn gating that runs for every natural spawn attempt: day and night. */
class SpawnDayNightTest {

    @Test
    void nightOnlyAllowsNight() {
        SpawnRule nightOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                Boolean.FALSE, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(nightOnly, 0, 0, 0, false, false, false),
                "night must satisfy a night-only rule");
    }

    @Test
    void nightOnlyRejectsDay() {
        SpawnRule nightOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                Boolean.FALSE, null, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(nightOnly, 0, 0, 0, true, false, false),
                "day must not satisfy a night-only rule");
    }

    @Test
    void dayOnlyAllowsDay() {
        SpawnRule dayOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                Boolean.TRUE, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(dayOnly, 0, 0, 0, true, false, false),
                "day must satisfy a day-only rule");
    }

    @Test
    void dayOnlyRejectsNight() {
        SpawnRule dayOnly = SpawnRuleFixtures.rule(null, null, null, null, null, null,
                Boolean.TRUE, null, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(dayOnly, 0, 0, 0, false, false, false),
                "night must not satisfy a day-only rule");
    }
}
