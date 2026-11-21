package com.luatweaker.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Drives {@code Particle:SpawnOptions} and its validation through a real engine. */
class ParticleOptionsBindingTest extends WorldEditFixture {

    @Test
    void spawnOptionsForwardsDustPayload() {
        ParticleLuaBinding.registerBindings(engine, new ParticleServiceImpl());

        executeLua(engine, """
                Particle:SpawnOptions('minecraft:dust', 1, 64, 2,
                        { Color = '#FF0000', Size = 2.0 }, 4, 0.1)
                """, "particle_options");

        assertDustOptions();
    }

    @Test
    void mixedOptionsAreRejected() {
        ParticleLuaBinding.registerBindings(engine, new ParticleServiceImpl());

        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow("""
                        Particle:SpawnOptions('minecraft:dust', 0, 0, 0,
                                { Color = '#FF0000', Block = 'minecraft:stone' })
                        """, "particle_bad_options"),
                "dust Color and Block cannot be mixed");
    }

    private void assertDustOptions() {
        assertEquals(1, recorder.particleOptionCalls.size(),
                "the option particle must reach the platform once");
        var call = recorder.particleOptionCalls.get(0);
        assertEquals("minecraft:dust", call.particleId(), "the particle id must be forwarded");
        assertEquals(0xFF0000, call.options().colorRgb(), "the hex color must be parsed");
        assertEquals(2.0, call.options().scale(), 1.0E-9, "the dust scale must be forwarded");
        assertEquals(4, call.spawn().count(), "the count must be forwarded");
    }
}
