package com.luatweaker.platform.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Volume and pitch must stay inside the audible range. */
class SoundVolumePitchTest {

    @Test
    void negativeVolumeClampsToZero() {
        assertEquals(0.0f, SoundHelper.volume(-3.0), 1e-9,
                "a negative volume must clamp to silence");
    }

    @Test
    void normalVolumePassesThrough() {
        assertEquals(2.5f, SoundHelper.volume(2.5), 1e-9,
                "a normal volume must pass through unchanged");
    }

    @Test
    void zeroPitchClampsToMinimum() {
        assertEquals(SoundHelper.MIN_PITCH, SoundHelper.pitch(0.0), 1e-9,
                "a zero pitch must clamp to the minimum audible pitch");
    }

    @Test
    void negativePitchClampsToMinimum() {
        assertEquals(SoundHelper.MIN_PITCH, SoundHelper.pitch(-1.0), 1e-9,
                "a negative pitch must clamp to the minimum audible pitch");
    }

    @Test
    void normalPitchPassesThrough() {
        assertEquals(1.4f, SoundHelper.pitch(1.4), 1e-9,
                "a normal pitch must pass through unchanged");
    }
}
