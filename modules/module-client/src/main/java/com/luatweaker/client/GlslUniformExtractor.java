package com.luatweaker.client;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts user-declared GLSL uniforms from a fragment shader body so the
 * generated Minecraft {@code program/*.json} descriptor binds every active
 * scalar and vector uniform location in {@code EffectInstance}.
 */
final class GlslUniformExtractor {

    private static final Pattern UNIFORM_DECL = Pattern.compile(
            "\\buniform\\s+(?:(?:highp|mediump|lowp)\\s+)?"
                    + "(float|int|vec2|vec3|vec4|ivec2|ivec3|ivec4)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^\\]]*\\])?\\s*;");

    private static final Set<String> ENGINE_UNIFORMS = Set.of(
            "DiffuseSampler", "ProjMat", "InSize", "OutSize", "ScreenSize", "Time");

    private GlslUniformExtractor() {
    }

    static @NotNull List<String> extractJsonEntries(@NotNull String source) {
        String[] maskedLines = GlslCommentMasker.mask(source.split("\n", -1));
        String masked = String.join("\n", maskedLines);
        Matcher matcher = UNIFORM_DECL.matcher(masked);
        Set<String> seen = new HashSet<>(ENGINE_UNIFORMS);
        List<String> entries = new ArrayList<>();
        while (matcher.find()) {
            String glslType = matcher.group(1);
            String name = matcher.group(2);
            if (seen.add(name)) {
                entries.add(toJsonEntry(name, glslType));
            }
        }
        return entries;
    }

    private static String toJsonEntry(String name, String glslType) {
        return switch (glslType) {
            case "float" -> floatEntry(name, 1, "0.0");
            case "vec2" -> floatEntry(name, 2, "0.0, 0.0");
            case "vec3" -> floatEntry(name, 3, "0.0, 0.0, 0.0");
            case "vec4" -> floatEntry(name, 4, "0.0, 0.0, 0.0, 0.0");
            case "int" -> intEntry(name, 1, "0");
            case "ivec2" -> intEntry(name, 2, "0, 0");
            case "ivec3" -> intEntry(name, 3, "0, 0, 0");
            case "ivec4" -> intEntry(name, 4, "0, 0, 0, 0");
            default -> floatEntry(name, 1, "0.0");
        };
    }

    private static String floatEntry(String name, int count, String values) {
        return "{\"name\": \"" + name + "\", \"type\": \"float\", \"count\": "
                + count + ", \"values\": [" + values + "]}";
    }

    private static String intEntry(String name, int count, String values) {
        return "{\"name\": \"" + name + "\", \"type\": \"int\", \"count\": "
                + count + ", \"values\": [" + values + "]}";
    }
}
