package com.luatweaker.client;

/**
 * Register-time lint for fragment-shader bodies.
 *
 * <p>Catches the mistakes a driver would only report at load time (in-game log),
 * with <b>line numbers</b> while the author is still looking at the source.
 * Driver-side errors (type mismatches, undeclared identifiers) cannot be caught
 * without a GL context; those surface through the post-activation check in the
 * platform layer, which points back at the driver's info log.</p>
 */
public final class GlslLint {

    private GlslLint() {}

    /**
     * @param passId id being registered (only used to name the failure)
     * @param body user fragment body, already stripped
     * @throws IllegalArgumentException naming the pass, line and fix
     */
    public static void lintFragmentBody(String passId, String body) {
        requireBody(passId, body);
        String[] lines = body.split("\n", -1);
        String[] masked = GlslCommentMasker.mask(lines);
        for (int i = 0; i < masked.length; i++) {
            int lineNo = i + 1;
            GlslForbiddenDeclarations.check(passId, lineNo, masked[i]);
            GlslLegacySyntax.check(passId, lineNo, masked[i]);
        }
        GlslBalanceChecker.check(passId, masked);
    }

    private static void requireBody(String passId, String body) {
        if (body == null || body.isBlank()) {
            GlslLintSupport.fail(passId, 0,
                    "needs a non-blank fragment body.");
        }
        String flat = body.replaceAll("\\s+", "");
        if (!flat.contains("voidmain")) {
            GlslLintSupport.fail(passId, 0, "body must contain 'void main'.");
        }
    }
}
