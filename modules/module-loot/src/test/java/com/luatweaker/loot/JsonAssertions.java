package com.luatweaker.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural sanity checks for generated loot-table JSON: strings must be closed
 * and braces/brackets must balance without a closer preceding its opener.
 */
final class JsonAssertions {

    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';
    private static final char OPEN_BRACE = '{';
    private static final char CLOSE_BRACE = '}';
    private static final char OPEN_BRACKET = '[';
    private static final char CLOSE_BRACKET = ']';

    private JsonAssertions() {
    }

    /** Throws an {@link AssertionError} when the JSON skeleton is malformed. */
    static void assertValid(String json) {
        String skeleton = withoutStrings(json);
        assertEquals(0, depthAfter(skeleton, OPEN_BRACE, CLOSE_BRACE), "braces must balance in: " + json);
        assertEquals(0, depthAfter(skeleton, OPEN_BRACKET, CLOSE_BRACKET), "brackets must balance in: " + json);
    }

    static boolean containsAll(String json, String... fragments) {
        for (String fragment : fragments) {
            if (!json.contains(fragment)) {
                return false;
            }
        }
        return true;
    }

    static boolean containsNone(String json, String... fragments) {
        for (String fragment : fragments) {
            if (json.contains(fragment)) {
                return false;
            }
        }
        return true;
    }

    private static String withoutStrings(String json) {
        StringBuilder skeleton = new StringBuilder(json.length());
        boolean inString = false;
        int index = 0;
        while (index < json.length()) {
            char c = json.charAt(index);
            if (inString && c == ESCAPE) {
                index += 2;
                continue;
            }
            if (c == QUOTE) {
                inString = !inString;
            } else if (!inString) {
                skeleton.append(c);
            }
            index++;
        }
        assertFalse(inString, "unterminated JSON string in: " + json);
        return skeleton.toString();
    }

    private static int depthAfter(String skeleton, char open, char close) {
        int depth = 0;
        for (char c : skeleton.toCharArray()) {
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
            }
            assertTrue(depth >= 0, "a closing bracket must not precede its opener in: " + skeleton);
        }
        return depth;
    }
}
