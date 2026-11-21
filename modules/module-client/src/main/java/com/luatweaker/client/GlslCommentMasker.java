package com.luatweaker.client;

/**
 * Blanks comments and string literals in a fragment body while preserving line numbers,
 * so the lint rules never match text that the compiler also ignores.
 */
final class GlslCommentMasker {

    private static final char QUOTE = '"';
    private static final char SLASH = '/';
    private static final char STAR = '*';
    private static final char SPACE = ' ';

    private GlslCommentMasker() {}

    static String[] mask(String... lines) {
        String[] masked = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            masked[i] = maskLine(lines[i]);
        }
        // Multi-line /* */ comments: blank them while keeping newlines.
        return maskBlockComments(lines, masked);
    }

    /** Masks // comments and "..." strings to spaces (newlines preserved). */
    static String maskLine(String line) {
        StringBuilder out = new StringBuilder(line.length());
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                out.append(SPACE);
                if (c == QUOTE) {
                    inString = false;
                }
                continue;
            }
            if (c == QUOTE) {
                inString = true;
                out.append(SPACE);
                continue;
            }
            if (c == SLASH && i + 1 < line.length() && line.charAt(i + 1) == SLASH) {
                out.append(" ".repeat(line.length() - i));
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String[] maskBlockComments(String[] lines, String... masked) {
        String[] result = masked.clone();
        BlockState state = new BlockState();
        for (int i = 0; i < lines.length; i++) {
            result[i] = maskBlockLine(result[i], state);
        }
        return result;
    }

    private static final class BlockState {
        private boolean inBlock;
    }

    private static String maskBlockLine(String line, BlockState state) {
        StringBuilder out = new StringBuilder(line.length());
        out.append(line);
        int j = 0;
        while (j < out.length() - 1) {
            if (!state.inBlock && isBlockStart(out, j)) {
                state.inBlock = true;
                blankPair(out, j);
                j += 2;
            } else if (state.inBlock && isBlockEnd(out, j)) {
                state.inBlock = false;
                blankPair(out, j);
                j += 2;
            } else {
                if (state.inBlock) {
                    out.setCharAt(j, SPACE);
                }
                j++;
            }
        }
        if (state.inBlock && out.length() > 0) {
            out.setCharAt(out.length() - 1, SPACE);
        }
        return out.toString();
    }

    private static boolean isBlockStart(StringBuilder out, int index) {
        return out.charAt(index) == SLASH && out.charAt(index + 1) == STAR;
    }

    private static boolean isBlockEnd(StringBuilder out, int index) {
        return out.charAt(index) == STAR && out.charAt(index + 1) == SLASH;
    }

    private static void blankPair(StringBuilder out, int index) {
        out.setCharAt(index, SPACE);
        out.setCharAt(index + 1, SPACE);
    }
}
