package com.luatweaker.interaction;

import org.junit.jupiter.api.Test;

/** Pins the line and circle generators of the particle service. */
class ParticleLineCircleTest {

    @Test
    void lineIncludesBothEndpointsAtTheRequestedSpacing() {
        ParticleShapeAssertions.assertLineSamples();
    }

    @Test
    void lineRejectsNonPositiveSpacing() {
        ParticleShapeAssertions.assertLineRejectsSpacing();
    }

    @Test
    void circleKeepsEveryPointOnTheRequestedRadius() {
        ParticleShapeAssertions.assertCircleSamples();
    }

    @Test
    void circleRejectsTooFewPoints() {
        ParticleShapeAssertions.assertCircleRejectsTooFewPoints();
    }
}
