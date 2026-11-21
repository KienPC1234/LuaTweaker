package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frame-level envelope rules: version, trailing data, payload cap and error truncation. */
class RpcEnvelopeDecodeTest {

    private static final String REQUEST = "req";
    private static final String FUNCTION = "Echo";
    private static final int UNKNOWN_VERSION = 99;

    private static RpcEnvelope valid() {
        return new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, "", new byte[0]);
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] full = valid().encode();
        byte[] extra = Arrays.copyOf(full, full.length + 1);

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(extra),
            "bytes left after the payload must be rejected, not ignored");
    }

    @Test
    void trailingBytesErrorNamesTrailingData() {
        byte[] full = valid().encode();
        byte[] extra = Arrays.copyOf(full, full.length + 1);

        String message = NetworkTestFixture.errorMessage(() -> RpcEnvelope.decode(extra));

        assertTrue(message.contains("trailing"),
            "the trailing-data rejection must say so, got: " + message);
    }

    @Test
    void unknownVersionIsRejected() {
        byte[] full = valid().encode();
        full[0] = (byte) UNKNOWN_VERSION;

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(full),
            "an unknown envelope version must be rejected");
    }

    @Test
    void oversizedDeclaredPayloadIsRejected() {
        byte[] declared = declaredPayloadFrame(LuaPayloadCodec.MAX_MESSAGE_BYTES + 1);

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(declared),
            "a declared payload above the message cap must be rejected");
    }

    @Test
    void oversizedPayloadErrorNamesTheMaximum() {
        byte[] declared = declaredPayloadFrame(LuaPayloadCodec.MAX_MESSAGE_BYTES + 1);

        String message = NetworkTestFixture.errorMessage(() -> RpcEnvelope.decode(declared));

        assertTrue(message.contains("max"),
            "the payload-cap rejection must name the maximum, got: " + message);
    }

    @Test
    void truncateErrorFitsTheCap() {
        String truncated = RpcEnvelope.truncateError("e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 100));

        assertEquals(RpcEnvelope.MAX_ERROR_CHARS, truncated.length(),
            "the truncated error must fit exactly inside the character cap");
    }

    @Test
    void truncateErrorMarksTruncation() {
        String truncated = RpcEnvelope.truncateError("e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 100));

        assertTrue(truncated.endsWith("..."), "a truncated error must be marked with an ellipsis");
    }

    @Test
    void truncatedErrorIsAcceptedByTheEnvelope() {
        String truncated = RpcEnvelope.truncateError("e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 100));

        assertDoesNotThrow(
            () -> new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, truncated, new byte[0]),
            "a truncated error must pass the envelope's own length validation");
    }

    private static byte[] declaredPayloadFrame(int declaredPayloadBytes) {
        BinaryWriter out = new BinaryWriter();
        out.writeByte(1);
        out.writeUtf8(REQUEST);
        out.writeByte(RpcEnvelope.PHASE_INVOKE);
        out.writeUtf8(FUNCTION);
        out.writeUtf8("");
        out.writeVarInt(declaredPayloadBytes);
        return out.toByteArray();
    }
}
