package com.luatweaker.platform.spawn;

import com.luatweaker.spawn.SpawnRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spawn gating that runs for every natural spawn attempt: Y ranges. */
class SpawnYRangeTest {

    @Test
    void minYIsInclusive() {
        SpawnRule rule = SpawnRuleFixtures.rule(-64, 63, null, null, null, null, null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(rule, -64, 0, 0, true, false, false),
                "the minimum Y must still allow spawning");
    }

    @Test
    void maxYIsInclusive() {
        SpawnRule rule = SpawnRuleFixtures.rule(-64, 63, null, null, null, null, null, null, null);

        assertTrue(SpawnRuleEvaluator.scalarConditionsAllow(rule, 63, 0, 0, true, false, false),
                "the maximum Y must still allow spawning");
    }

    @Test
    void belowMinYIsRejected() {
        SpawnRule rule = SpawnRuleFixtures.rule(-64, 63, null, null, null, null, null, null, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(rule, -65, 0, 0, true, false, false),
                "one below the minimum Y must reject spawning");
    }

    @Test
    void aboveMaxYIsRejected() {
        SpawnRule rule = SpawnRuleFixtures.rule(-64, 63, null, null, null, null, null, null, null);

        assertFalse(SpawnRuleEvaluator.scalarConditionsAllow(rule, 64, 0, 0, true, false, false),
                "one above the maximum Y must reject spawning");
    }
}
