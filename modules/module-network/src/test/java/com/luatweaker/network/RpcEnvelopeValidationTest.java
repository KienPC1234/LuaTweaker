package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Envelope construction and decode validation: identities, bounds and truncation. */
class RpcEnvelopeValidationTest {

    private static final String REQUEST = "req";
    private static final String FUNCTION = "Echo";
    private static final byte UNKNOWN_PHASE = (byte) 9;

    private static RpcEnvelope valid() {
        return new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, "", new byte[0]);
    }

    @Test
    void unknownPhaseIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope(REQUEST, UNKNOWN_PHASE, FUNCTION, "", new byte[0]),
            "only the invoke and result phases are legal");
    }

    @Test
    void blankRequestIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope("  ", RpcEnvelope.PHASE_INVOKE, FUNCTION, "", new byte[0]),
            "a blank request id must be rejected");
    }

    @Test
    void blankFunctionNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, " ", "", new byte[0]),
            "a blank function name must be rejected");
    }

    @Test
    void overlongRequestIdIsRejected() {
        String tooLong = "r".repeat(RpcEnvelope.MAX_ID_CHARS + 1);

        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope(tooLong, RpcEnvelope.PHASE_INVOKE, FUNCTION, "", new byte[0]),
            "a request id above the character cap must be rejected");
    }

    @Test
    void overlongFunctionNameIsRejected() {
        String tooLong = "n".repeat(RpcEnvelope.MAX_NAME_CHARS + 1);

        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, tooLong, "", new byte[0]),
            "a function name above the character cap must be rejected");
    }

    @Test
    void overlongErrorIsRejected() {
        String tooLong = "e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 1);

        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, tooLong, new byte[0]),
            "an error text above the character cap must be rejected");
    }

    @Test
    void truncatedInputIsRejected() {
        byte[] full = valid().encode();
        byte[] cut = Arrays.copyOf(full, full.length - 1);

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(cut),
            "a payload cut off before its last field must be rejected");
    }

    @Test
    void emptyInputIsRejected() {
        byte[] empty = new byte[0];

        assertThrows(IllegalArgumentException.class, () -> RpcEnvelope.decode(empty),
            "an empty payload has no version byte and must be rejected");
    }
}
