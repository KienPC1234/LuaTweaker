package com.luatweaker.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LuaTweakerCommonLoadWarningTest {

    @Test
    void cleanLoadWithEmptyErrorsProducesNoLines() {
        assertTrue(LuaTweakerCommon.buildLoadWarningLines(Map.of(), 5).isEmpty(),
                "a clean load must not warn the player");
    }

    @Test
    void cleanLoadWithNullErrorsProducesNoLines() {
        assertTrue(LuaTweakerCommon.buildLoadWarningLines(null, 5).isEmpty(),
                "missing error details must not warn the player either");
    }

    private static List<String> sortedFailureLines() {
        Map<String, String> errors = new ConcurrentHashMap<>();
        errors.put("zeta_mod", "LuaMod 'lib' is not loaded");
        errors.put("alpha_mod", "Requires Minecraft >= 1.22 (running 1.21.1)");

        return LuaTweakerCommon.buildLoadWarningLines(errors, 5);
    }

    @Test
    void failuresRenderFourLinesWithSummaryAndHint() {
        assertEquals(4, sortedFailureLines().size(),
                "two failures must render as summary + one line per mod + doctor hint");
    }

    @Test
    void failuresSummaryNamesTheMissingCount() {
        assertTrue(sortedFailureLines().get(0).contains("2 LuaMod(s) did not load"),
                "the summary line must name how many mods failed: " + sortedFailureLines());
    }

    @Test
    void failuresAreSortedByModId() {
        assertTrue(sortedFailureLines().get(1).contains("alpha_mod"),
                "failures must be sorted by id so output is stable: " + sortedFailureLines());
    }

    @Test
    void failuresListEveryMod() {
        assertTrue(sortedFailureLines().get(2).contains("zeta_mod"),
                "every failed mod must get its own line: " + sortedFailureLines());
    }

    @Test
    void failuresPointAtDoctor() {
        assertTrue(sortedFailureLines().get(3).contains("/lt doctor"),
                "the last line must point the player at /lt doctor: " + sortedFailureLines());
    }
}
