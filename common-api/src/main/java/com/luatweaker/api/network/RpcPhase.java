package com.luatweaker.api.network;

/**
 * Wire phases of the RemoteFunction request/response protocol. Both sides send the same
 * envelope; the phase tells the receiver whether it must run the function ({@link #INVOKE})
 * or hand a result back to the waiting caller ({@link #RESULT}).
 */
public final class RpcPhase {

    /** Sender asks the receiver to execute the named RemoteFunction handler. */
    public static final String INVOKE = "invoke";

    /** Receiver answers an {@link #INVOKE}; {@code dataJson} holds the return value. */
    public static final String RESULT = "result";

    private RpcPhase() {
    }
}
