package com.luatweaker.platform.runservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tick deltas must stay within the nominal frame budget. */
class ServerTickClockTest {

    private static double[] twoDeltas() {
        return new double[] {ServerTickClock.nextDelta(), ServerTickClock.nextDelta()};
    }

    @Test
    void firstDeltaIsBounded() {
        double first = twoDeltas()[0];

        assertTrue(first >= ServerTickClock.NOMINAL_DELTA_SECONDS && first <= 1.0,
                "first=" + first);
    }

    @Test
    void secondDeltaIsBounded() {
        double second = twoDeltas()[1];

        assertTrue(second >= ServerTickClock.NOMINAL_DELTA_SECONDS && second <= 1.0,
                "second=" + second);
    }
}
