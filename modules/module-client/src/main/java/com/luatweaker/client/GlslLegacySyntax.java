package com.luatweaker.client;

/**
 * Detects GLSL 1.00 syntax that does not exist in the engine's 150 pipeline.
 */
final class GlslLegacySyntax {

    private static final char UNDERSCORE = '_';
    private static final char LOWER_A = 'a';
    private static final char LOWER_Z = 'z';
    private static final char UPPER_A = 'A';
    private static final char UPPER_Z = 'Z';
    private static final char DIGIT_0 = '0';
    private static final char DIGIT_9 = '9';

    private GlslLegacySyntax() {}

    static void check(String passId, int lineNo, String line) {
        checkTexture2D(passId, lineNo, line);
        checkFragColor(passId, lineNo, line);
        checkWord(passId, lineNo, line, "attribute",
                "'attribute' is GLSL 1.00; use 'in' in this 150 pipeline.");
        checkWord(passId, lineNo, line, "varying",
                "'varying' is GLSL 1.00; use 'in'/'out' in this 150 pipeline.");
    }

    private static void checkTexture2D(String passId, int lineNo, String line) {
        if (line.contains("texture2D(")) {
            GlslLintSupport.fail(passId, lineNo,
                    "'texture2D(' is GLSL 1.00; use 'texture(' in this 150 pipeline.");
        }
    }

    private static void checkFragColor(String passId, int lineNo, String line) {
        if (line.contains("gl_FragColor")) {
            GlslLintSupport.fail(passId, lineNo,
                    "'gl_FragColor' doesn't exist here; write to 'fragColor'.");
        }
    }

    private static void checkWord(String passId, int lineNo, String line, String word, String fix) {
        if (hasWord(line, word)) {
            GlslLintSupport.fail(passId, lineNo, fix);
        }
    }

    private static boolean hasWord(String line, String word) {
        int from = 0;
        while (true) {
            int at = line.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            boolean leftOk = at == 0 || !isWordChar(line.charAt(at - 1));
            int end = at + word.length();
            boolean rightOk = end >= line.length() || !isWordChar(line.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = end;
        }
    }

    private static boolean isWordChar(char c) {
        return c == UNDERSCORE || (c >= LOWER_A && c <= LOWER_Z)
                || (c >= UPPER_A && c <= UPPER_Z) || (c >= DIGIT_0 && c <= DIGIT_9);
    }
}
