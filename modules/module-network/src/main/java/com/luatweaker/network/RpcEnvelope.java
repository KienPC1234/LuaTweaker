package com.luatweaker.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RemoteFunction request/response envelope.
 *
 * <p>It travels inside the same frame type as RemoteEvents, on its own channel, so chunk
 * reassembly, size limits and the inbound rate limiter apply once instead of twice. The
 * Lua arguments themselves are still produced by {@link LuaPayloadCodec}.</p>
 */
public record RpcEnvelope(String requestId, byte phase, String functionName, String error,
                          byte[] payload) {

    public static final byte PHASE_INVOKE = 0;
    public static final byte PHASE_RESULT = 1;

    public static final int MAX_ID_CHARS = 64;
    public static final int MAX_NAME_CHARS = 128;
    public static final int MAX_ERROR_CHARS = 4_096;

    /** UTF-8 worst case is 4 bytes per char; the decoder enforces these hard bounds. */
    static final int MAX_ID_BYTES = MAX_ID_CHARS * 4;
    static final int MAX_NAME_BYTES = MAX_NAME_CHARS * 4;
    static final int MAX_ERROR_BYTES = MAX_ERROR_CHARS * 4;

    private static final byte ENVELOPE_VERSION = 1;
    private static final byte[] NO_PAYLOAD = new byte[0];

    public RpcEnvelope {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("RemoteFunction request id must not be empty");
        }
        if (requestId.length() > MAX_ID_CHARS) {
            throw new IllegalArgumentException(
                    "RemoteFunction request id is longer than " + MAX_ID_CHARS + " chars");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("RemoteFunction name must not be empty");
        }
        if (functionName.length() > MAX_NAME_CHARS) {
            throw new IllegalArgumentException(
                    "RemoteFunction name '" + functionName + "' is longer than " + MAX_NAME_CHARS + " chars");
        }
        if (phase != PHASE_INVOKE && phase != PHASE_RESULT) {
            throw new IllegalArgumentException("Unknown RemoteFunction phase " + phase);
        }
        if (error != null && error.length() > MAX_ERROR_CHARS) {
            throw new IllegalArgumentException(
                    "RemoteFunction error text is longer than " + MAX_ERROR_CHARS + " chars");
        }
        error = error == null ? "" : error;
        payload = payload == null ? NO_PAYLOAD : payload;
    }

    public byte @NotNull [] encode() {
        BinaryWriter out = new BinaryWriter();
        out.writeByte(ENVELOPE_VERSION);
        out.writeUtf8(requestId);
        out.writeByte(phase);
        out.writeUtf8(functionName);
        out.writeUtf8(error);
        out.writeVarInt(payload.length);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    /**
     * @throws IllegalArgumentException on any malformed, truncated or oversized envelope
     */
    public static @NotNull RpcEnvelope decode(byte @NotNull [] data) {
        BinaryReader in = new BinaryReader(data, 0, data.length);
        int version = in.readByte();
        if (version != ENVELOPE_VERSION) {
            throw new IllegalArgumentException("Unsupported RemoteFunction envelope version " + version);
        }
        String requestId = in.readUtf8(MAX_ID_BYTES);
        byte phase = (byte) in.readByte();
        String functionName = in.readUtf8(MAX_NAME_BYTES);
        String error = in.readUtf8(MAX_ERROR_BYTES);
        int length = in.readPositiveVarInt("payload length");
        if (length > LuaPayloadCodec.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException(
                    "RemoteFunction payload is " + length + " bytes (max " + LuaPayloadCodec.MAX_MESSAGE_BYTES + ")");
        }
        byte[] payload = in.readBytes(length);
        in.requireFinished();
        return new RpcEnvelope(requestId, phase, functionName, error, payload);
    }

    /**
     * Keeps an exception message from becoming an unbounded packet field.
     *
     * @return the text cut to {@link #MAX_ERROR_CHARS}, marked as truncated
     */
    public static @NotNull String truncateError(@Nullable String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= MAX_ERROR_CHARS) {
            return message;
        }
        return message.substring(0, MAX_ERROR_CHARS - 3) + "...";
    }
}
