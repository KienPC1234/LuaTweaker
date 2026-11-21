package com.luatweaker.network;

import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frame-level decode rules: version, truncation, trailing data and unknown tags. */
class LuaPayloadCodecFrameTest {

    private static final int UNKNOWN_TAG = 99;
    private static final int UNKNOWN_VERSION = 2;

    LuavaEngine engine;
    byte[] validPayload;

    @BeforeEach
    void setup() {
        engine = new LuavaEngine(false);
        validPayload = LuaPayloadCodec.encodeArgs(engine.wrapNumber(1));
    }

    @Test
    void encodedPayloadStartsWithTheVersionByte() {
        assertEquals(LuaPayloadCodec.VERSION, validPayload[0],
            "every encoded payload must start with the codec version byte");
    }

    @Test
    void truncatedArrayPayloadIsRejected() {
        byte[] payload = {LuaPayloadCodec.VERSION, 1, 6};

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "a payload cut off inside an array must be rejected");
    }

    @Test
    void truncatedHeaderPayloadIsRejected() {
        byte[] payload = {LuaPayloadCodec.VERSION};

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "a payload ending after the version byte must be rejected");
    }

    @Test
    void emptyPayloadIsRejected() {
        byte[] payload = new byte[0];

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "an empty payload has no version byte and must be rejected");
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] withGarbage = new byte[validPayload.length + 1];
        System.arraycopy(validPayload, 0, withGarbage, 0, validPayload.length);
        withGarbage[validPayload.length] = 0x7F;

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, withGarbage),
            "bytes left after the last argument must be rejected, not ignored");
    }

    @Test
    void trailingErrorNamesTrailingData() {
        byte[] withGarbage = new byte[validPayload.length + 1];
        System.arraycopy(validPayload, 0, withGarbage, 0, validPayload.length);
        withGarbage[validPayload.length] = 0x7F;

        String message = NetworkTestFixture.errorMessage(() -> LuaPayloadCodec.decodeArgs(engine, withGarbage));

        assertTrue(message.contains("trailing"),
            "the trailing-data rejection must say so, got: " + message);
    }

    @Test
    void unknownVersionIsRejected() {
        byte[] payload = {(byte) UNKNOWN_VERSION, 0};

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "an unknown codec version must be rejected");
    }

    @Test
    void unknownTagIsRejected() {
        byte[] payload = {LuaPayloadCodec.VERSION, 1, (byte) UNKNOWN_TAG};

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "an unknown value tag must be rejected");
    }
}
