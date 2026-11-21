package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@code ClientEffects:PlaySound/PlaySoundAt}: the default volume/pitch/source/loop tail, the
 * Vector3-and-numbers argument forms and the stop-by-handle round trip.
 */
class ClientSoundTest extends ClientServiceFixture {

    private static final String HARP = "minecraft:block.note_block.harp";
    private static final String CAVE = "minecraft:ambient.cave";

    /** Plays one sound with only an id: every optional argument must fall back to its default. */
    private void playWithDefaults() {
        engine.executeStringOrThrow("ClientEffects:PlaySound('" + HARP + "')", "SoundDefaults");
    }

    private List<Object> defaultsState() {
        return List.of(client.lastSoundId, client.lastVolume, client.lastPitch, client.lastSource,
                client.lastLoop);
    }

    /** Plays one positional looping sound, stops it twice, then stops by id and by category. */
    private void playPositionalLoopAndStop() {
        engine.executeStringOrThrow("""
                local handle = ClientEffects:PlaySoundAt('minecraft:ambient.cave', 10, 20, 30,
                    0.5, 0.8, 'AMBIENT', true)
                assert(handle == 41, 'the platform handle must reach Lua, got ' .. tostring(handle))
                assert(ClientEffects:StopSound(handle) == true)
                assert(ClientEffects:StopSound(handle) == false,
                    'a stale handle must report false')
                assert(ClientEffects:GetActiveSoundCount() == 0)
                ClientEffects:StopSounds('minecraft:ambient.cave', 'AMBIENT')
                assert(ClientEffects:StopAllSounds() == 3)
                """, "SoundHandle");
    }

    private List<Object> loopState() {
        return List.of(client.lastX, client.lastY, client.lastZ, client.lastSource, client.lastLoop,
                client.lastStoppedSounds, client.lastStoppedSource, client.stopAllCalls);
    }

    @Test
    void soundDefaultsReachThePlatform() {
        playWithDefaults();
        assertEquals(List.of(HARP, 1.0, 1.0, "MASTER", false), defaultsState(),
                "PlaySound must default volume/pitch to 1.0, source to MASTER and loop to false");
    }

    @Test
    void positionalLoopingSoundRoundTripsHandleAndState() {
        playPositionalLoopAndStop();
        assertEquals(List.of(10.0, 20.0, 30.0, "AMBIENT", true, CAVE, "AMBIENT", 1), loopState(),
                "PlaySoundAt must forward the position/source/loop and StopSounds the id and source");
    }
}
