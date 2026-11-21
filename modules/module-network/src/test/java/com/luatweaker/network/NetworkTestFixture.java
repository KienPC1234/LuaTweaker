package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Shared helpers for the network tests: engine binding, envelope decoding and error text. */
final class NetworkTestFixture {

    static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000000";
    static final String OTHER_PLAYER_UUID = "00000000-0000-0000-0000-000000000001";

    private NetworkTestFixture() {
    }

    static NetworkServiceImpl service(ILuaEngine engine) {
        NetworkServiceImpl service = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, service);
        return service;
    }

    static ILuaValue[] decodeArgs(ILuaEngine engine, Frame frame) {
        return LuaPayloadCodec.decodeArgs(engine, frame.data());
    }

    static ILuaValue[] roundTrip(ILuaEngine engine, ILuaValue... values) {
        return LuaPayloadCodec.decodeArgs(engine, LuaPayloadCodec.encodeArgs(values));
    }

    static RpcEnvelope decodeEnvelope(Frame frame) {
        return RpcEnvelope.decode(frame.data());
    }

    /** Runs an action expected to fail and returns its message; fails when nothing was thrown. */
    static String errorMessage(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            return String.valueOf(error.getMessage());
        }
        throw new AssertionError("expected an IllegalArgumentException but the action completed normally");
    }

    static byte[] bytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] largeChunkedMessage() {
        byte[] message = new byte[ChunkAssembler.CHUNK_BYTES * 2 + 5];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i * 31);
        }
        return message;
    }

    static byte[] complete(ChunkAssembler assembler, String key, String channel, byte[] message) {
        List<byte[]> chunks = ChunkAssembler.split(message);
        byte[] result = null;
        for (int index = 0; index < chunks.size(); index++) {
            result = assembler.accept(key, channel, index, chunks.size(), chunks.get(index));
        }
        return result;
    }
}
