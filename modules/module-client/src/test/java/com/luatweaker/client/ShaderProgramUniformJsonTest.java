package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression suite for shader program JSON generation: {@code OutSize} must
 * always be emitted for {@code minecraft:blit.vsh}, and all user/builtin
 * scalar and vector uniforms must be bound in {@code program/*.json}.
 */
class ShaderProgramUniformJsonTest extends ShaderTestFixture {

    @Test
    void alwaysEmitsOutSizeForBlitVertexShader() {
        service.registerGlslPass("minimal_pass", """
                void main() {
                    fragColor = texture(DiffuseSampler, texCoord);
                }
                """);

        String json = programJson("minimal_pass");
        assertTrue(json != null && json.contains("\"name\": \"OutSize\""),
                "program JSON must always declare OutSize so minecraft:blit.vsh never divides by (0, 0): " + json);
    }

    @Test
    void extractsRadarVec3AndScalarUniformsIntoProgramJson() {
        service.registerGlslPass("radar_probe", """
                uniform vec3 BlipA;
                uniform vec3 BlipB;
                uniform float Count;
                uniform float Strength;
                void main() {
                    vec4 base = texture(DiffuseSampler, texCoord);
                    float pulse = fract(Time) + ScreenSize.x * 0.0 + Count + Strength + BlipA.x + BlipB.y;
                    fragColor = vec4(base.rgb * clamp(pulse, 0.0, 1.0), base.a);
                }
                """);

        String json = programJson("radar_probe");
        boolean allBound = json != null
                && json.contains("\"name\": \"OutSize\"")
                && json.contains("\"name\": \"ScreenSize\"")
                && json.contains("\"name\": \"Time\"")
                && json.contains("{\"name\": \"BlipA\", \"type\": \"float\", \"count\": 3, \"values\": [0.0, 0.0, 0.0]}")
                && json.contains("{\"name\": \"BlipB\", \"type\": \"float\", \"count\": 3, \"values\": [0.0, 0.0, 0.0]}")
                && json.contains("{\"name\": \"Count\", \"type\": \"float\", \"count\": 1, \"values\": [0.0]}")
                && json.contains("{\"name\": \"Strength\", \"type\": \"float\", \"count\": 1, \"values\": [0.0]}");

        assertTrue(allBound,
                "radar shader program JSON must bind OutSize, ScreenSize, Time, vec3 blips, and float uniforms: " + json);
    }

    @Test
    void ignoresCommentedUniformDeclarations() {
        service.registerGlslPass("comment_probe", """
                // uniform float CommentedScalar;
                /* uniform vec4 CommentedVec; */
                uniform vec2 ActiveOffset;
                void main() {
                    fragColor = texture(DiffuseSampler, texCoord + ActiveOffset);
                }
                """);

        String json = programJson("comment_probe");
        boolean clean = json != null
                && json.contains("\"name\": \"ActiveOffset\"")
                && !json.contains("CommentedScalar")
                && !json.contains("CommentedVec");

        assertTrue(clean,
                "commented-out uniform lines must not be emitted into program JSON: " + json);
    }

    @Test
    void builtinEffectsEmitTheirCustomUniformsAndOutSize() {
        service.setEffect("sepia", null);

        String json = programJson("builtin_sepia");
        assertTrue(json != null
                        && json.contains("\"name\": \"OutSize\"")
                        && json.contains("\"name\": \"Strength\""),
                "built-in shader passes must declare OutSize and their custom uniforms in program JSON: " + json);
    }
}
