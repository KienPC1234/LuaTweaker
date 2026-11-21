package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A result envelope must round-trip its error and payload, and reject malformed shapes. */
class RpcResultEnvelopeTest {

    private static final String ERROR_TEXT = "handler failed";

    private static RpcEnvelope resultEnvelope() {
        return new RpcEnvelope(RpcEnvelopeFixtures.REQUEST_ID, RpcEnvelope.PHASE_RESULT,
            RpcEnvelopeFixtures.FUNCTION_NAME, ERROR_TEXT, new byte[]{7, 8});
    }

    @Test
    void resultKeepsPhase() {
        assertEquals(RpcEnvelope.PHASE_RESULT, RpcEnvelopeFixtures.decode(resultEnvelope()).phase(),
            "the result phase must survive the round trip");
    }

    @Test
    void resultKeepsErrorMessage() {
        assertEquals(ERROR_TEXT, RpcEnvelopeFixtures.decode(resultEnvelope()).error(),
            "the error message must survive the round trip");
    }

    @Test
    void resultKeepsPayloadBytes() {
        assertArrayEquals(new byte[]{7, 8}, RpcEnvelopeFixtures.decode(resultEnvelope()).payload(),
            "the payload bytes must survive the round trip");
    }

    @Test
    void nullErrorBecomesEmptyString() {
        RpcEnvelope envelope = new RpcEnvelope(RpcEnvelopeFixtures.REQUEST_ID, RpcEnvelope.PHASE_RESULT,
            RpcEnvelopeFixtures.FUNCTION_NAME, null, null);

        assertEquals("", envelope.error(), "a null error must decode as an empty string");
    }

    @Test
    void nullPayloadBecomesEmptyArray() {
        RpcEnvelope envelope = new RpcEnvelope(RpcEnvelopeFixtures.REQUEST_ID, RpcEnvelope.PHASE_RESULT,
            RpcEnvelopeFixtures.FUNCTION_NAME, "", null);

        assertEquals(0, envelope.payload().length, "a null payload must decode as an empty array");
    }

    @Test
    void overlongErrorIsRejected() {
        String tooLong = "e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 1);

        assertThrows(IllegalArgumentException.class,
            () -> new RpcEnvelope(RpcEnvelopeFixtures.REQUEST_ID, RpcEnvelope.PHASE_RESULT,
                RpcEnvelopeFixtures.FUNCTION_NAME, tooLong, new byte[0]),
            "an error longer than the cap must be rejected");
    }

    @Test
    void truncateErrorRespectsTheCap() {
        String truncated = RpcEnvelope.truncateError("e".repeat(RpcEnvelope.MAX_ERROR_CHARS + 100));

        assertEquals(RpcEnvelope.MAX_ERROR_CHARS, truncated.length(),
            "the truncated error must fit exactly inside the cap");
    }
}
