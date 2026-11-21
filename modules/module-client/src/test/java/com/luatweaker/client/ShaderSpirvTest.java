package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code registerSpirvPass}: base64/garbage input is rejected, the SPIR-V magic is checked
 * before translation, and a minimal module is translated into a published fragment.
 */
class ShaderSpirvTest extends ShaderTestFixture {

    private static final String NOT_BASE64 = "!!!not-base64!!!";
    private static final String TINY_PASS = "tiny";

    /** A hand-assembled minimal SPIR-V module (magic header + main with a float output). */
    private static byte[] minimalSpirv() {
        int[] words = {
                0x07230203, 0x00010000, 0x00000000, 0x00000005, 0x00000000,
                0x00020011, 0x00000001,
                0x0003000E, 0x00000000, 0x00000001,
                0x0005000F, 0x00000004, 0x00000003, 0x6E69616D, 0x00000000,
                0x00030010, 0x00000003, 0x00000007,
                0x00020013, 0x00000001,
                0x00030021, 0x00000002, 0x00000001,
                0x00050036, 0x00000001, 0x00000003, 0x00000000, 0x00000002,
                0x000200F8, 0x00000004,
                0x000100FD,
                0x00010038,
        };
        ByteBuffer buffer = ByteBuffer.allocate(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().put(words);
        return buffer.array();
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String nonSpirvRejection() {
        String notSpirv = base64("hello world, not spirv!!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return assertThrows(IllegalArgumentException.class,
                () -> service.registerSpirvPass("s1", notSpirv),
                "a payload without the SPIR-V magic must be rejected").getMessage();
    }

    @Test
    void spirvRejectsInvalidBase64() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerSpirvPass("s1", NOT_BASE64),
                "a non-base64 SPIR-V payload must be rejected");
    }

    @Test
    void spirvRejectsNonSpirvPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerSpirvPass("s1", base64("plain text".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8))),
                "a decoded payload without the SPIR-V magic must be rejected");
    }

    @Test
    void spirvErrorNamesTheMagic() {
        assertTrue(nonSpirvRejection().contains("magic"),
                "the SPIR-V rejection must name the missing magic: " + nonSpirvRejection());
    }

    @Test
    void spirvRejectsBadPassId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerSpirvPass("BAD ID", base64(minimalSpirv())),
                "an invalid pass id must be rejected before any translation work");
    }

    @Test
    void spirvTranslatesAndPublishesFragment() {
        service.registerSpirvPass(TINY_PASS, base64(minimalSpirv()));
        String fragment = fragmentSource(TINY_PASS);
        assertEquals(List.of(true, true, true),
                List.of(fragment != null, fragment != null && fragment.contains("void main"),
                        fragment != null && fragment.contains("#version")),
                "the translator must publish GLSL carrying its own version header and a main()");
    }

    @Test
    void spirvPassActivates() {
        service.registerSpirvPass(TINY_PASS, base64(minimalSpirv()));
        service.activatePass(TINY_PASS);
        assertEquals(TINY_PASS, service.getActivePass(),
                "a translated SPIR-V pass must be activatable like a GLSL pass");
    }
}
