package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Envelope fields and payload survive the encode/decode round trip. */
class RpcEnvelopeRoundTripTest {

    private static final String REQUEST = "req";
    private static final String FUNCTION = "Echo";
    private static final String ERROR_TEXT = "handler failed";

    private static byte[] payload() {
        return new byte[]{1, 2, 3};
    }

    private static RpcEnvelope roundTripped(RpcEnvelope envelope) {
        return RpcEnvelope.decode(envelope.encode());
    }

    @Test
    void invokeEnvelopeKeepsItsFields() {
        RpcEnvelope decoded = roundTripped(
            new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, "", payload()));

        assertEquals(List.of(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, ""),
            List.of(decoded.requestId(), decoded.phase(), decoded.functionName(), decoded.error()),
            "request id, phase, function name and error must survive the round trip");
    }

    @Test
    void invokeEnvelopeKeepsItsPayload() {
        RpcEnvelope decoded = roundTripped(
            new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, "", payload()));

        assertArrayEquals(payload(), decoded.payload(),
            "the invoke payload bytes must survive the round trip");
    }

    @Test
    void resultEnvelopeKeepsPhaseAndError() {
        RpcEnvelope decoded = roundTripped(
            new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_RESULT, FUNCTION, ERROR_TEXT, new byte[0]));

        assertEquals(List.of(RpcEnvelope.PHASE_RESULT, ERROR_TEXT),
            List.of(decoded.phase(), decoded.error()),
            "the result phase and error message must survive the round trip");
    }

    @Test
    void resultEnvelopeKeepsEmptyPayload() {
        RpcEnvelope decoded = roundTripped(
            new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_RESULT, FUNCTION, ERROR_TEXT, new byte[0]));

        assertEquals(0, decoded.payload().length, "an empty result payload must stay empty");
    }

    @Test
    void nullErrorBecomesEmptyString() {
        RpcEnvelope envelope = new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, null, null);

        assertEquals("", envelope.error(), "a null error must be normalized to an empty string");
    }

    @Test
    void nullPayloadBecomesEmptyArray() {
        RpcEnvelope envelope = new RpcEnvelope(REQUEST, RpcEnvelope.PHASE_INVOKE, FUNCTION, null, null);

        assertEquals(0, envelope.payload().length, "a null payload must be normalized to an empty array");
    }
}
