package com.luatweaker.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModLoadWarningOverflowTest {

    private static List<String> overflowLines() {
        Map<String, String> errors = new ConcurrentHashMap<>();
        for (int index = 0; index < 7; index++) {
            errors.put("mod_" + index, "reason " + index);
        }
        errors.put("mod_long", "x".repeat(200));

        return LuaTweakerCommon.buildLoadWarningLines(errors, 5);
    }

    @Test
    void overflowRendersSevenLines() {
        assertEquals(7, overflowLines().size(),
                "eight failures with a cap of five must render as five lines plus summary");
    }

    @Test
    void overflowSummarizesTheHiddenRest() {
        assertTrue(overflowLines().get(6).contains("3 more"),
                "the last line must summarize the failures that did not fit: " + overflowLines());
    }

    @Test
    void overflowTruncatesPathologicalReasons() {
        assertTrue(overflowLines().stream().noneMatch(line -> line.length() > 160),
                "one pathological reason must not flood chat: " + overflowLines());
    }
}
