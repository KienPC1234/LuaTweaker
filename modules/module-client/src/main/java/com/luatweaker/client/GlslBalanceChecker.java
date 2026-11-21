package com.luatweaker.client;

/**
 * Bracket/brace/paren balance check that reports the line where an unmatched delimiter
 * was opened. Works on the comment-masked body so delimiters in comments/strings are ignored.
 */
final class GlslBalanceChecker {

    private static final int MAX_DEPTH = 1024;
    private static final char OPEN_BRACE = '{';
    private static final char OPEN_PAREN = '(';
    private static final char OPEN_BRACKET = '[';
    private static final char CLOSE_BRACE = '}';
    private static final char CLOSE_PAREN = ')';
    private static final char CLOSE_BRACKET = ']';

    private GlslBalanceChecker() {}

    static void check(String passId, String... masked) {
        char[] stack = new char[MAX_DEPTH];
        int[] stackLines = new int[MAX_DEPTH];
        int depth = 0;
        for (int i = 0; i < masked.length; i++) {
            String line = masked[i];
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (isOpen(c)) {
                    depth = push(passId, i + 1, c, depth, stack, stackLines);
                } else if (isClose(c)) {
                    depth = pop(passId, i + 1, c, depth, stack, stackLines);
                }
            }
        }
        if (depth > 0) {
            GlslLintSupport.fail(passId, stackLines[depth - 1],
                    "unbalanced '" + stack[depth - 1] + "' — never closed.");
        }
    }

    private static boolean isOpen(char c) {
        return c == OPEN_BRACE || c == OPEN_PAREN || c == OPEN_BRACKET;
    }

    private static boolean isClose(char c) {
        return c == CLOSE_BRACE || c == CLOSE_PAREN || c == CLOSE_BRACKET;
    }

    private static int push(String passId, int lineNo, char open, int depth,
                            char[] stack, int... stackLines) {
        if (depth >= stack.length) {
            GlslLintSupport.fail(passId, lineNo, "nesting too deep (over "
                    + MAX_DEPTH + " open delimiters).");
        }
        stack[depth] = open;
        stackLines[depth] = lineNo;
        return depth + 1;
    }

    private static int pop(String passId, int lineNo, char close, int depth,
                           char[] stack, int... stackLines) {
        if (depth == 0) {
            GlslLintSupport.fail(passId, lineNo, "unbalanced '" + close + "' — nothing to close here.");
        }
        char open = stack[depth - 1];
        if (!matches(open, close)) {
            GlslLintSupport.fail(passId, lineNo, "unbalanced '" + close + "' — expected '"
                    + closerFor(open) + "' for the '" + open + "' opened at line "
                    + stackLines[depth - 1] + ".");
        }
        return depth - 1;
    }

    private static boolean matches(char open, char close) {
        return (open == OPEN_BRACE && close == CLOSE_BRACE)
                || (open == OPEN_PAREN && close == CLOSE_PAREN)
                || (open == OPEN_BRACKET && close == CLOSE_BRACKET);
    }

    private static char closerFor(char open) {
        if (open == OPEN_PAREN) {
            return CLOSE_PAREN;
        }
        if (open == OPEN_BRACKET) {
            return CLOSE_BRACKET;
        }
        return CLOSE_BRACE;
    }
}
