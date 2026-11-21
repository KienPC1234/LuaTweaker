package com.luatweaker.interaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins World:PlaySound / World:StopSound / World:SpawnParticle argument parsing: numeric and
 * Vector3 positions, optional volume/pitch/source tail, sound-source vs dimension disambiguation,
 * and the refusal to default a missing position to the world origin.
 */
class WorldEffectBindingTest extends WorldEditFixture {

    @Test
    void playSoundRecordsPositionVolumePitchAndSource() {
        executeLua(engine, """
                World:PlaySound('minecraft:entity.experience_orb.pickup', 1, 2, 3, 0.5, 1.5,
                        'HOSTILE')
                World:PlaySound('luatweaker:crystal_realm', 'minecraft:block.note_block.harp',
                        { X = 4, Y = 5, Z = 6 }, 0.25, 2.0, 'BLOCKS')
                """, "play_sound_args");

        assertEquals(List.of(
                        new WorldEditRecorder.SoundCall(OVERWORLD,
                                "minecraft:entity.experience_orb.pickup", List.of(1.0, 2.0, 3.0),
                                0.5f, 1.5f, "HOSTILE"),
                        new WorldEditRecorder.SoundCall("luatweaker:crystal_realm",
                                "minecraft:block.note_block.harp", List.of(4.0, 5.0, 6.0),
                                0.25f, 2.0f, "BLOCKS")),
                recorder.soundCalls,
                "PlaySound must accept numeric coordinates and a Vector3 table while forwarding "
                        + "volume, pitch and source");
    }

    @Test
    void playSoundWithoutPositionNeverReachesThePlatform() {
        executeLua(engine, """
                assert(World:PlaySound('minecraft:entity.experience_orb.pickup') == nil)
                assert(World:PlaySound('minecraft:entity.experience_orb.pickup', 'PLAYERS') == nil)
                World:SpawnParticle('minecraft:flame')
                """, "sound_missing_position");

        assertTrue(recorder.soundCalls.isEmpty(),
                "defaulting a missing position to 0,0,0 would play at the world origin");
    }

    @Test
    void stopSoundDisambiguatesSourceFromDimension() {
        executeLua(engine, """
                assert(World:StopSound('minecraft:block.note_block.harp', 'PLAYERS') == true)
                assert(World:StopSound('minecraft:the_nether', 'minecraft:block.note_block.harp')
                        == true)
                """, "stop_sound_source");

        assertEquals(List.of(
                        new WorldEditRecorder.StopCall(OVERWORLD, "minecraft:block.note_block.harp",
                                "PLAYERS"),
                        new WorldEditRecorder.StopCall(NETHER, "minecraft:block.note_block.harp",
                                "PLAYERS")),
                recorder.stopCalls,
                "a known sound-category string must be read as the source, not as a dimension");
    }

    @Test
    void particleArgumentsAreFullyParsed() {
        executeLua(engine, """
                World:SpawnParticle('minecraft:flame', { X = 1, Y = 2, Z = 3 },
                        { X = 0.1, Y = 0.2, Z = 0.3 }, 12, 0.5)
                """, "particle_vector_args");

        assertEquals(new WorldEditRecorder.ParticleCall(OVERWORLD, "minecraft:flame",
                        List.of(1.0, 2.0, 3.0), List.of(0.1, 0.2, 0.3), 12, 0.5),
                recorder.particleCalls.get(0),
                "SpawnParticle must parse position, velocity, count and speed from Lua tables");
    }
}
