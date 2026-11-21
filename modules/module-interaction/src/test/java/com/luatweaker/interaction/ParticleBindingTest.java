package com.luatweaker.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Drives the Lua-facing {@code Particle} shape service through a real engine and the recording
 * PAL double.
 */
class ParticleBindingTest extends WorldEditFixture {

    @Test
    void lineDispatchesEveryGeneratedPoint() {
        ParticleLuaBinding.registerBindings(engine, new ParticleServiceImpl());

        executeLua(engine, """
                Particle:Line('minecraft:flame', 0, 64, 0, 1, 64, 0, 0.5)
                """, "particle_line");

        assertLineCalls();
    }

    @Test
    void bezierReadsVector3ControlPoints() {
        ParticleLuaBinding.registerBindings(engine, new ParticleServiceImpl());

        executeLua(engine, """
                Particle:Bezier('minecraft:end_rod', {
                    { X = 0, Y = 64, Z = 0 },
                    { X = 1, Y = 66, Z = 0 },
                    { X = 2, Y = 64, Z = 0 },
                }, 5)
                """, "particle_bezier");

        assertBezierCalls();
    }

    @Test
    void circleUsesTheRequestedDimension() {
        ParticleLuaBinding.registerBindings(engine, new ParticleServiceImpl());

        executeLua(engine, """
                Particle:Circle('minecraft:enchanted_hit', 0, 64, 0, 2, 8, 0,
                        'minecraft:the_nether')
                """, "particle_circle");

        assertCircleCalls();
    }

    @Test
    void invalidShapeIsRejectedBeforeReachingThePlatform() {
        assertInvalidShapeRejected();
    }

    private void assertLineCalls() {
        assertEquals(3, recorder.particleCalls.size(),
                "a 1-block line at 0.5 spacing must reach the platform three times");
        assertEquals(OVERWORLD, recorder.particleCalls.get(0).dimension(),
                "the default dimension must be the overworld");
        assertEquals("minecraft:flame", recorder.particleCalls.get(0).particleId(),
                "the particle id must be forwarded unchanged");
        assertEquals(0.0, recorder.particleCalls.get(0).position().get(0), 1.0E-9,
                "the first sample must start at the requested X");
        assertEquals(1.0, recorder.particleCalls.get(2).position().get(0), 1.0E-9,
                "the last sample must end at the requested X");
    }

    private void assertBezierCalls() {
        assertEquals(5, recorder.particleCalls.size(),
                "the requested sample count must be forwarded");
        assertEquals(64.0, recorder.particleCalls.get(0).position().get(1), 1.0E-9,
                "the first Bezier sample must sit on the first control point");
        assertEquals(65.0, recorder.particleCalls.get(2).position().get(1), 1.0E-9,
                "the middle sample must follow the quadratic curve");
        assertEquals(64.0, recorder.particleCalls.get(4).position().get(1), 1.0E-9,
                "the last sample must sit on the last control point");
    }

    private void assertCircleCalls() {
        assertEquals(8, recorder.particleCalls.size(), "eight points must be dispatched");
        assertEquals(NETHER, recorder.particleCalls.get(0).dimension(),
                "the explicit dimension argument must be used");
    }

    private void assertInvalidShapeRejected() {
        ParticleServiceImpl service = new ParticleServiceImpl();
        assertThrows(IllegalArgumentException.class,
                () -> service.circle("minecraft:flame", 0, 0, 0, 0, 8, 0, OVERWORLD),
                "a zero radius must fail loudly instead of spawning at one point");
        assertThrows(IllegalArgumentException.class,
                () -> service.spawn("minecraft:flame", 0, 0, 0, 0, 0, 0, 0, 0, OVERWORLD),
                "count 0 must fail loudly instead of silently spawning nothing");
    }
}
