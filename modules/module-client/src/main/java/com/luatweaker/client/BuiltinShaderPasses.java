package com.luatweaker.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in fragment-shader bodies for the shader-backed named effects.
 *
 * <p>Every body is a fragment <b>body only</b> in the engine subset: it may use
 * {@code DiffuseSampler}, {@code Time}, {@code InSize}, {@code OutSize},
 * {@code ScreenSize}, {@code texCoord} and write {@code fragColor}, plus the
 * {@code uniform float} names listed in its spec. Bodies are registered through
 * the same {@code registerGlslPass} machinery as user passes, so they get the
 * same validation, virtual files and disk cache.</p>
 */
public final class BuiltinShaderPasses {

    /** One float uniform: Lua param name, GLSL name, default, min, max. */
    public record UniformSpec(String param, String uniform, double def, double min, double max) {}

    /** A built-in pass: id suffix, body, and its float uniforms. */
    public record BuiltinPass(String id, String body, List<UniformSpec> uniforms) {}

    private BuiltinShaderPasses() {}

    private static final String MAIN_OPEN = "void main() {\n";
    private static final String SAMPLE_BASE = "    vec4 base = texture(DiffuseSampler, texCoord);\n";
    private static final String BODY_CLOSE = "}\n";
    private static final String STRENGTH_UNIFORM = "uniform float Strength;\n";

    private static UniformSpec uniform(String param, String uniform, double def, double min, double max) {
        return new UniformSpec(param, uniform, def, min, max);
    }

    private static UniformSpec strength(double def) {
        return new UniformSpec("strength", "Strength", def, 0.0, 1.0);
    }

    public static final Map<String, BuiltinPass> ALL = buildAll();

    private static Map<String, BuiltinPass> buildAll() {
        Map<String, BuiltinPass> all = new ConcurrentHashMap<>();
        all.put("grade", new BuiltinPass("builtin_grade",
                "uniform float Brightness;\n"
                        + "uniform float Contrast;\n"
                        + "uniform float Saturation;\n"
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    vec3 c = (base.rgb - 0.5) * Contrast + 0.5 + Brightness;\n"
                        + "    float lum = dot(c, vec3(0.299, 0.587, 0.114));\n"
                        + "    c = mix(vec3(lum), c, Saturation);\n"
                        + "    fragColor = vec4(c, base.a);\n"
                        + BODY_CLOSE,
                List.of(uniform("brightness", "Brightness", 0.0, -1.0, 1.0),
                        uniform("contrast", "Contrast", 1.0, 0.0, 3.0),
                        uniform("saturation", "Saturation", 1.0, 0.0, 3.0))));
        all.put("sepia", new BuiltinPass("builtin_sepia",
                STRENGTH_UNIFORM
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    vec3 sep = vec3(dot(base.rgb, vec3(0.393, 0.769, 0.189)),\n"
                        + "                    dot(base.rgb, vec3(0.349, 0.686, 0.168)),\n"
                        + "                    dot(base.rgb, vec3(0.272, 0.534, 0.131)));\n"
                        + "    fragColor = vec4(mix(base.rgb, sep, Strength), base.a);\n"
                        + BODY_CLOSE,
                List.of(strength(0.8))));
        all.put("noir", new BuiltinPass("builtin_noir",
                STRENGTH_UNIFORM
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));\n"
                        + "    vec3 noir = vec3(lum) * vec3(1.05, 1.0, 0.95);\n"
                        + "    fragColor = vec4(mix(base.rgb, noir, Strength), base.a);\n"
                        + BODY_CLOSE,
                List.of(strength(0.9))));
        all.put("temperature", new BuiltinPass("builtin_temperature",
                "uniform float Amount;\n"
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    vec3 c = base.rgb;\n"
                        + "    c.r += Amount * 0.15;\n"
                        + "    c.b -= Amount * 0.15;\n"
                        + "    fragColor = vec4(c, base.a);\n"
                        + BODY_CLOSE,
                List.of(uniform("amount", "Amount", 0.5, -1.0, 1.0))));
        all.put("nightVision", new BuiltinPass("builtin_nightvision",
                STRENGTH_UNIFORM
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));\n"
                        + "    vec3 nv = vec3(lum * 0.6, lum * 1.0, lum * 0.6);\n"
                        + "    float scan = 0.92 + 0.08 * sin(texCoord.y * ScreenSize.y * 3.14159);\n"
                        + "    fragColor = vec4(mix(base.rgb, nv * scan, Strength), base.a);\n"
                        + BODY_CLOSE,
                List.of(strength(1.0))));
        all.put("grain", new BuiltinPass("builtin_grain",
                STRENGTH_UNIFORM
                        + "float hash(vec2 p) {\n"
                        + "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                        + BODY_CLOSE
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    float g = hash(texCoord * ScreenSize + fract(Time) * 100.0) - 0.5;\n"
                        + "    fragColor = vec4(base.rgb + g * Strength, base.a);\n"
                        + BODY_CLOSE,
                List.of(strength(0.4))));
        all.put("blur", new BuiltinPass("builtin_blur",
                "uniform float Radius;\n"
                        + MAIN_OPEN
                        + "    vec2 px = vec2(max(Radius, 0.0)) / ScreenSize;\n"
                        + "    vec3 c = texture(DiffuseSampler, texCoord).rgb * 0.2270270270;\n"
                        + "    vec2 o1 = px * 1.3846153846;\n"
                        + "    vec2 o2 = px * 3.2307692308;\n"
                        + "    c += texture(DiffuseSampler, texCoord + vec2(o1.x, 0.0)).rgb * 0.3162162162;\n"
                        + "    c += texture(DiffuseSampler, texCoord - vec2(o1.x, 0.0)).rgb * 0.3162162162;\n"
                        + "    c += texture(DiffuseSampler, texCoord + vec2(o2.x, 0.0)).rgb * 0.0702702703;\n"
                        + "    c += texture(DiffuseSampler, texCoord - vec2(o2.x, 0.0)).rgb * 0.0702702703;\n"
                        + SAMPLE_BASE
                        + "    fragColor = vec4(c, base.a);\n"
                        + BODY_CLOSE,
                List.of(uniform("radius", "Radius", 3.0, 0.0, 10.0))));
        all.put("radialBlur", new BuiltinPass("builtin_radialblur",
                STRENGTH_UNIFORM
                        + MAIN_OPEN
                        + "    vec2 dir = texCoord - vec2(0.5);\n"
                        + "    vec3 c = vec3(0.0);\n"
                        + "    for (int i = 0; i < 8; i++) {\n"
                        + "        float scale = 1.0 - Strength * 0.15 * (float(i) / 7.0);\n"
                        + "        c += texture(DiffuseSampler, vec2(0.5) + dir * scale).rgb;\n"
                        + "    }\n"
                        + SAMPLE_BASE
                        + "    fragColor = vec4(c / 8.0, base.a);\n"
                        + BODY_CLOSE,
                List.of(strength(0.6))));
        all.put("chromaticAberration", new BuiltinPass("builtin_chromatic",
                "uniform float Offset;\n"
                        + MAIN_OPEN
                        + "    vec2 px = vec2(Offset) / ScreenSize;\n"
                        + "    float r = texture(DiffuseSampler, texCoord + px).r;\n"
                        + "    float b = texture(DiffuseSampler, texCoord - px).b;\n"
                        + SAMPLE_BASE
                        + "    fragColor = vec4(r, base.g, b, base.a);\n"
                        + BODY_CLOSE,
                List.of(uniform("offset", "Offset", 2.0, 0.0, 10.0))));
        all.put("pixelate", new BuiltinPass("builtin_pixelate",
                "uniform float CellSize;\n"
                        + MAIN_OPEN
                        + "    vec2 cells = ScreenSize / max(CellSize, 1.0);\n"
                        + "    vec2 uv = (floor(texCoord * cells) + 0.5) / cells;\n"
                        + "    fragColor = texture(DiffuseSampler, uv);\n"
                        + BODY_CLOSE,
                List.of(uniform("cellSize", "CellSize", 8.0, 2.0, 64.0))));
        all.put("glitch", new BuiltinPass("builtin_glitch",
                "uniform float Intensity;\n"
                        + "float hash(vec2 p) {\n"
                        + "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                        + BODY_CLOSE
                        + MAIN_OPEN
                        + "    float row = floor(texCoord.y * ScreenSize.y);\n"
                        + "    float shift = (hash(vec2(row, floor(Time * 12.0))) - 0.5) * Intensity * 0.2;\n"
                        + "    vec2 uv = vec2(fract(texCoord.x + shift), texCoord.y);\n"
                        + "    float r = texture(DiffuseSampler, uv + vec2(Intensity * 0.01, 0.0)).r;\n"
                        + "    float b = texture(DiffuseSampler, uv - vec2(Intensity * 0.01, 0.0)).b;\n"
                        + "    vec4 base = texture(DiffuseSampler, uv);\n"
                        + "    fragColor = vec4(r, base.g, b, base.a);\n"
                        + BODY_CLOSE,
                List.of(uniform("intensity", "Intensity", 0.5, 0.0, 1.0))));
        all.put("scanlines", new BuiltinPass("builtin_scanlines",
                STRENGTH_UNIFORM
                        + MAIN_OPEN
                        + SAMPLE_BASE
                        + "    float line = 0.85 + 0.15 * step(0.5, fract(texCoord.y * ScreenSize.y / 3.0));\n"
                        + "    fragColor = vec4(base.rgb * mix(1.0, line, Strength), base.a);\n"
                        + BODY_CLOSE,
                List.of(strength(0.6))));
        all.put("wobble", new BuiltinPass("builtin_wobble",
                "uniform float Amplitude;\n"
                        + "uniform float Frequency;\n"
                        + MAIN_OPEN
                        + "    vec2 uv = texCoord;\n"
                        + "    uv.x += sin(uv.y * Frequency + Time * 3.0) * Amplitude / ScreenSize.x;\n"
                        + "    uv.y += cos(uv.x * Frequency + Time * 2.0) * Amplitude / ScreenSize.y;\n"
                        + "    fragColor = texture(DiffuseSampler, uv);\n"
                        + BODY_CLOSE,
                List.of(uniform("amplitude", "Amplitude", 4.0, 0.0, 20.0),
                        uniform("frequency", "Frequency", 3.0, 0.0, 10.0))));
        return Map.copyOf(all);
    }
}
