package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import static com.luatweaker.network.RpcEnvelopeFixtures.FUNCTION_NAME;
import static com.luatweaker.network.RpcEnvelopeFixtures.REQUEST_ID;
import static com.luatweaker.network.RpcEnvelopeFixtures.decode;
import static org.junit.jupiter.api.Assertions.*;

/** An invoke envelope must survive the encode/decode round trip field by field. */
class RpcInvokeEnvelopeTest {

    private static RpcEnvelope roundTripped() {
        byte[] payload = {1, 2, 3};
        RpcEnvelope envelope =
                new RpcEnvelope(REQUEST_ID, RpcEnvelope.PHASE_INVOKE, FUNCTION_NAME, "", payload);
        return decode(envelope);
    }

    @Test
    void invokeKeepsRequestId() {
        assertEquals(REQUEST_ID, roundTripped().requestId(), "the request id must survive the round trip");
    }

    @Test
    void invokeKeepsPhase() {
        assertEquals(RpcEnvelope.PHASE_INVOKE, roundTripped().phase(),
                "the phase must survive the round trip");
    }

    @Test
    void invokeKeepsFunctionName() {
        assertEquals(FUNCTION_NAME, roundTripped().functionName(),
                "the function name must survive the round trip");
    }

    @Test
    void invokeKeepsEmptyError() {
        assertEquals("", roundTripped().error(), "an empty error must stay empty");
    }

    @Test
    void invokeKeepsPayloadBytes() {
        assertArrayEquals(new byte[] {1, 2, 3}, roundTripped().payload(),
                "the payload bytes must survive the round trip");
    }

    @Test
    void unknownPhaseIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RpcEnvelope(REQUEST_ID, (byte) 9, FUNCTION_NAME, "", new byte[0]),
                "only invoke and result phases are legal");
    }
}
