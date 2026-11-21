package com.luatweaker.network;

/** Shared string fixtures for the RPC envelope tests. Small, immutable, no behavior. */
final class RpcEnvelopeFixtures {

    static final String FUNCTION_NAME = "Echo";
    static final String REQUEST_ID = "req-1";

    private RpcEnvelopeFixtures() {
    }

    static RpcEnvelope decode(RpcEnvelope envelope) {
        return RpcEnvelope.decode(envelope.encode());
    }
}
