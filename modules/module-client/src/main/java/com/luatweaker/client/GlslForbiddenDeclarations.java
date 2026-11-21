package com.luatweaker.client;

/**
 * Rejects declarations that the engine already injects. Each check names the exact fix so
 * the author can edit the body without reading engine source.
 */
final class GlslForbiddenDeclarations {

    private GlslForbiddenDeclarations() {}

    static void check(String passId, int lineNo, String line) {
        checkVersion(passId, lineNo, line);
        checkUniformSampler(passId, lineNo, line);
        checkTexCoordInput(passId, lineNo, line);
        checkFragColorOutput(passId, lineNo, line);
    }

    private static void checkVersion(String passId, int lineNo, String line) {
        if (line.contains("#version")) {
            GlslLintSupport.fail(passId, lineNo,
                    "must NOT declare '#version' — the engine injects it. Write the body only.");
        }
    }

    private static void checkUniformSampler(String passId, int lineNo, String line) {
        if (line.contains("uniform") && line.contains("sampler2D")) {
            GlslLintSupport.fail(passId, lineNo,
                    "must NOT declare 'uniform sampler2D' — the engine declares DiffuseSampler."
                            + " Use it, don't redeclare it.");
        }
    }

    private static void checkTexCoordInput(String passId, int lineNo, String line) {
        if (line.contains("in") && line.contains("vec2") && line.contains("texCoord")) {
            GlslLintSupport.fail(passId, lineNo,
                    "must NOT declare 'in vec2 texCoord' — the engine declares it."
                            + " Use it, don't redeclare it.");
        }
    }

    private static void checkFragColorOutput(String passId, int lineNo, String line) {
        if (line.contains("out") && line.contains("vec4") && line.contains("fragColor")) {
            GlslLintSupport.fail(passId, lineNo,
                    "must NOT declare 'out vec4 fragColor' — the engine declares it."
                            + " Write to it, don't redeclare it.");
        }
    }
}
