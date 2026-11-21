package com.luatweaker.client;

/**
 * Shared failure formatting for the GLSL lint checks. A line number of 0 means the
 * problem is not tied to a specific line.
 */
final class GlslLintSupport {

    private GlslLintSupport() {}

    static void fail(String passId, int lineNo, String fix) {
        if (lineNo <= 0) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + passId + "') " + fix);
        }
        throw new IllegalArgumentException(
                "Shaders:registerGlslPass('" + passId + "') line " + lineNo + ": " + fix);
    }
}
