package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins GLSL pass registration: the validator rejects unusable programs (naming the problem), a
 * valid pass publishes the chain/program/fragment trio, and identical source is a cache hit.
 */
class ShaderGlslRegistrationTest extends ShaderTestFixture {

    private static final String VALID_ID = "ok";
    private static final String WARP = "warp";
    private static final String FRAGMENT_ONLY = "fragColor = vec4(1.0);";
    private static final String WITH_VERSION = "#version 150\nvoid main() {}";
    private static final String WITH_SAMPLER = "uniform sampler2D DiffuseSampler;\nvoid main() {}";
    private static final String WITH_TEXCOORD = "in vec2 texCoord;\nvoid main() {}";
    private static final String WITH_FRAG_COLOR = "out vec4 fragColor;\nvoid main() {}";

    private String rejectionOf(String id, String body) {
        return assertThrows(IllegalArgumentException.class,
                () -> service.registerGlslPass(id, body),
                "an unusable pass (" + id + ") must be rejected instead of published").getMessage();
    }

    /** Rejection reasons for every invalid id/body combination the validator must refuse. */
    private List<String> rejectionReasons() {
        List<String> reasons = new ArrayList<>();
        reasons.add(rejectionOf("UPPER", BODY));
        reasons.add(rejectionOf("has space", BODY));
        reasons.add(rejectionOf("", BODY));
        reasons.add(rejectionOf(VALID_ID, "   "));
        reasons.add(rejectionOf(VALID_ID, FRAGMENT_ONLY));
        reasons.add(rejectionOf(VALID_ID, WITH_VERSION));
        reasons.add(rejectionOf(VALID_ID, WITH_SAMPLER));
        reasons.add(rejectionOf(VALID_ID, WITH_TEXCOORD));
        reasons.add(rejectionOf(VALID_ID, WITH_FRAG_COLOR));
        reasons.add(rejectionOf(VALID_ID, "void main() {}\n" + "x".repeat(20_000)));
        return reasons;
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.contains(needle);
    }

    /** One boolean per property the published chain/program/fragment trio must carry. */
    private List<Object> publicationChecks() {
        String chain = chainJson(WARP);
        String program = programJson(WARP);
        String fragment = fragmentSource(WARP);
        return List.of(
                contains(chain, "\"name\": \"luatweaker:lt_warp\""),
                contains(chain, "\"intarget\": \"minecraft:main\""),
                contains(chain, "\"name\": \"minecraft:blit\""),
                contains(program, "\"vertex\": \"minecraft:blit\""),
                contains(program, "\"fragment\": \"luatweaker:lt_warp\""),
                contains(program, "\"DiffuseSampler\""),
                contains(fragment, "#version 150"),
                contains(fragment, "uniform sampler2D DiffuseSampler;"),
                contains(fragment, "uniform float Time;"),
                contains(fragment, "in vec2 texCoord;"),
                contains(fragment, "out vec4 fragColor;"),
                contains(fragment, "sin(Time * 3.0)"));
    }

    private File[] cacheMarkers() {
        return cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
    }

    @Test
    void invalidGlslPassSignaturesAreRejected() {
        assertTrue(rejectionReasons().stream().allMatch(reason -> reason != null && !reason.isBlank()),
                "every invalid pass id/source must be rejected with an explanation, not a null message");
    }

    @Test
    void registerPublishesThreeVirtualFiles() {
        service.registerGlslPass(WARP, BODY);
        assertEquals(List.of(true, true, true, true, true, true, true, true, true, true, true, true),
                publicationChecks(),
                "a registered pass must publish a chain reading/blitting the main target, a program "
                        + "with the injected uniforms and the user body verbatim");
    }

    @Test
    void cacheWritesAShaMarker() throws IOException {
        service.registerGlslPass(WARP, BODY);
        File[] markers = Objects.requireNonNull(cacheMarkers(),
                "registering a pass must write a sha cache marker");
        String marker = Files.readString(markers[0].toPath(), StandardCharsets.UTF_8);
        assertEquals(List.of(1, true), List.of(markers.length, marker.contains("\"id\": \"warp\"")),
                "the cache directory must hold exactly one marker that records the pass id");
    }

    @Test
    void identicalSourceIsACacheHit() {
        service.registerGlslPass(WARP, BODY);
        service.registerGlslPass(WARP, BODY);
        File[] markers = Objects.requireNonNull(cacheMarkers(),
                "re-registering identical source must keep the cache marker");
        assertEquals(List.of(1, 3), List.of(markers.length, datapack.files.size()),
                "a cache hit must not duplicate markers and must keep the three virtual files");
    }

    @Test
    void activateRejectsUnknownPass() {
        assertThrows(IllegalArgumentException.class, () -> service.activatePass("ghost"),
                "activating a pass that was never registered must fail loudly");
    }
}
