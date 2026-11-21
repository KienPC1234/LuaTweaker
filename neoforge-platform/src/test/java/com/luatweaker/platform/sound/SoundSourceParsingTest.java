package com.luatweaker.platform.sound;

import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sound source parsing must accept any case and fall back to the default. */
class SoundSourceParsingTest {

    @Test
    void lowercaseSourceResolves() {
        assertEquals(SoundSource.PLAYERS, SoundHelper.parseSource("players", SoundSource.NEUTRAL),
                "a lowercase source name must resolve to its channel");
    }

    @Test
    void uppercaseSourceResolves() {
        assertEquals(SoundSource.HOSTILE, SoundHelper.parseSource("HOSTILE", SoundSource.NEUTRAL),
                "an uppercase source name must resolve to its channel");
    }

    @Test
    void nullSourceFallsBack() {
        assertEquals(SoundSource.BLOCKS, SoundHelper.parseSource(null, SoundSource.BLOCKS),
                "a null source must fall back to the provided default");
    }

    @Test
    void unknownSourceFallsBack() {
        assertEquals(SoundSource.NEUTRAL, SoundHelper.parseSource("nonsense", SoundSource.NEUTRAL),
                "an unknown source must fall back to the provided default");
    }
}
