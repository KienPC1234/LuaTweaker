package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** RemoteFunction envelope: strict fields, strict bounds, no silent truncation on decode. */
class RpcEnvelopeTest {

    @Test
    void invokeEnvelopeRoundTrips() {
        byte[] payload = {1, 2, 3};
        RpcEnvelope envelope = new RpcEnvelope("req-1", RpcEnvelope.PHASE_INVOKE, "Echo", "", payload);

        RpcEnvelope decoded = RpcEnvelope.decode(envelope.encode());

        assertEquals("req-1", decoded.requestId());
        assertEquals(RpcEnvelope.PHASE_INVOKE, decoded.phase());
        assertEquals("Echo", decoded.functionName());
        assertEquals("", decoded.error());
        assertArrayEquals(payload, decoded.payload());
    }

    @Test
    void resultEnvelopeKeepsTheErrorMessage() {
        RpcEnvelope envelope = new RpcEnvelope("req-2", RpcEnvelope.PHASE_RESULT, "Boom",
                "handler failed", new byte[0]);

        RpcEnvelope decoded = RpcEnvelope.decode(envelope.encode());

        assertEquals("handler failed", decoded.error());
        assertEquals(RpcEnvelope.PHASE_RESULT, decoded.phase());
        assertEquals(0, decoded.payload().length);
    }

    @Test
    void nullErrorAndPayloadBecomeSafeDefaults() {
        RpcEnvelope envelope = new RpcEnvelope("req-3", RpcEnvelope.PHASE_INVOKE, "Echo", null, null);

        assertEquals("", envelope.error());
        assertEquals(0, envelope.payload().length);
    }

    @Test
    void unknownPhaseIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope("req", (byte) 9, "Echo", "", new byte[0]));
    }

    @Test
    void blankIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope("  ", RpcEnvelope.PHASE_INVOKE, "Echo", "", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE, " ", "", new byte[0]));
    }

    @Test
    void overlongFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope("r".repeat(RpcEnvelope.MAX_ID_CHARS + 1),
                        RpcEnvelope.PHASE_INVOKE, "Echo", "", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE,
                        "n".repeat(RpcEnvelope.MAX_NAME_CHARS + 1), "", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE, "Echo",
                        "e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 1), new byte[0]));
    }

    @Test
    void truncateErrorKeepsTheResultWithinTheLimit() {
        String truncated = RpcEnvelope.truncateError("e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 100));

        assertEquals(RpcEnvelope.MAX_ERROR_CHARS, truncated.length());
        assertTrue(truncated.endsWith("..."));
        assertDoesNotThrow(() -> new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE, "Echo",
                truncated, new byte[0]));
    }

    @Test
    void truncatedInputIsRejected() {
        byte[] full = new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE, "Echo", "", new byte[]{1})
                .encode();
        byte[] cut = Arrays.copyOf(full, full.length - 1);

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(cut));
        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(new byte[0]));
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] full = new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE, "Echo", "", new byte[0]).encode();
        byte[] extra = Arrays.copyOf(full, full.length + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RpcEnvelope.decode(extra));
        assertTrue(error.getMessage().contains("trailing"), error.getMessage());
    }

    @Test
    void unknownVersionIsRejected() {
        byte[] full = new RpcEnvelope("req", RpcEnvelope.PHASE_INVOKE, "Echo", "", new byte[0]).encode();
        full[0] = 99;

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(full));
    }

    @Test
    void oversizedDeclaredPayloadIsRejected() {
        BinaryWriter out = new BinaryWriter();
        out.writeByte(1);
        out.writeUtf8("req");
        out.writeByte(RpcEnvelope.PHASE_INVOKE);
        out.writeUtf8("Echo");
        out.writeUtf8("");
        out.writeVarInt(LuaPayloadCodec.MAX_MESSAGE_BYTES + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RpcEnvelope.decode(out.toByteArray()));
        assertTrue(error.getMessage().contains("max"), error.getMessage());
    }
}
