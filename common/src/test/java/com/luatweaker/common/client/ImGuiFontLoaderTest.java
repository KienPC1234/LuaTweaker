package com.luatweaker.common.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Font loading must stay readable, detect TTF bytes and build a usable atlas. */
class ImGuiFontLoaderTest {

    @Test
    void candidatesStayReadable() {
        List<Path> candidates = ImGuiFontLoader.candidates();

        assertFalse(candidates.isEmpty(),
                "there must always be at least one font candidate to try");
    }

    @Test
    void firstCandidateIsTheBundledConfigFont() {
        assertEquals(Path.of("config", "luatweaker_font.ttf"), ImGuiFontLoader.candidates().get(0),
                "the bundled config font must be tried before system fonts");
    }

    @Test
    void trueTypeMagicIsDetected() {
        assertTrue(ImGuiFontLoader.looksLikeTtf(new byte[] {0x00, 0x01, 0x00, 0x00}),
                "the TrueType sfnt magic must be recognized as a font");
    }

    @Test
    void openTypeMagicIsDetected() {
        assertTrue(ImGuiFontLoader.looksLikeTtf(new byte[] {'O', 'T', 'T', 'O'}),
                "the OpenType magic must be recognized as a font");
    }

    @Test
    void pngMagicIsRejected() {
        assertFalse(ImGuiFontLoader.looksLikeTtf(new byte[] {(byte) 0x89, 'P', 'N', 'G'}),
                "a PNG header must never be treated as font bytes");
    }

    @Test
    void emptyBytesAreRejected() {
        assertFalse(ImGuiFontLoader.looksLikeTtf(new byte[0]),
                "empty bytes must never be treated as font bytes");
    }

    @Test
    void nullBytesAreRejected() {
        assertFalse(ImGuiFontLoader.looksLikeTtf(null),
                "null bytes must never be treated as font bytes");
    }
}
