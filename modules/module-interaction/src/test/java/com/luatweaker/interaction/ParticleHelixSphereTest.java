package com.luatweaker.interaction;

import org.junit.jupiter.api.Test;

/** Pins the helix, sphere and Bezier generators of the particle service. */
class ParticleHelixSphereTest {

    @Test
    void helixRisesFromBaseToHeight() {
        ParticleShapeAssertions.assertHelixSamples();
    }

    @Test
    void helixRejectsNonPositiveTurns() {
        ParticleShapeAssertions.assertHelixRejectsZeroTurns();
    }

    @Test
    void spherePointsLieOnTheSurface() {
        ParticleBezierAssertions.assertSphereSamples();
    }

    @Test
    void bezierPassesThroughEndpointsAndMidpoint() {
        ParticleBezierAssertions.assertBezierSamples();
    }

    @Test
    void bezierRejectsIncompleteControlPoints() {
        ParticleBezierAssertions.assertBezierRejectsIncompleteControlPoints();
    }

    @Test
    void shapePointBudgetIsEnforced() {
        ParticleBezierAssertions.assertBudgetEnforced();
    }
}
