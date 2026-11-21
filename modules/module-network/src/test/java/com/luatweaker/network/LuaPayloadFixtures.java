package com.luatweaker.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Builds the malformed wire payloads used by the decoder rejection tests. Kept
 * apart from {@link NetworkTestFixture} so neither helper class crosses PMD's
 * method-count limit.
 */
final class LuaPayloadFixtures {

    private LuaPayloadFixtures() {
    }

    /** A version-1 payload whose arrays nest past {@code MAX_NESTING}. */
    static byte[] nestingPayload() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(LuaPayloadCodec.VERSION);
            out.write(1);
            for (int level = 0; level < LuaPayloadCodec.MAX_NESTING + 5; level++) {
                out.write(6);
                out.write(1);
            }
            out.write(0);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("in-memory payload assembly must not fail", e);
        }
    }

    /** A version-1 array payload that declares an element count above the cap. */
    static byte[] absurdCountPayload() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(LuaPayloadCodec.VERSION);
            out.write(0x81);
            out.write(0x80);
            out.write(0x04);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("in-memory payload assembly must not fail", e);
        }
    }
}
