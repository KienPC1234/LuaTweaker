package com.luatweaker.common.network;

import com.luatweaker.network.ChunkAssembler;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Shared codec and validation routines for LuaTweaker network frames.
 */
public final class LuaTweakerPayloadHelper {
    public static final int MAX_CHANNEL_CHARS = 128;
    public static final int MAX_FRAME_BYTES = ChunkAssembler.MAX_FRAME_BYTES;
    public static final int MIN_FRAME_TOTAL = 1;

    private LuaTweakerPayloadHelper() {
    }

    public static void validate(String channelName, int index, int total, byte[] data) {
        validateChannel(channelName);
        validateFrameBounds(index, total);
        if (data == null) {
            throw new IllegalArgumentException("Network frame data must not be null");
        }
    }

    private static void validateChannel(String channelName) {
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("Network frame channel must not be empty");
        }
    }

    private static void validateFrameBounds(int index, int total) {
        if (total < MIN_FRAME_TOTAL) {
            throw new IllegalArgumentException("Network frame total must be at least 1, got " + total);
        }
        if (index < 0 || index >= total) {
            throw new IllegalArgumentException("Network frame index " + index + " is outside 0.." + (total - 1));
        }
    }

    public static void encode(FriendlyByteBuf buf, String channelName, int index, int total, byte[] data) {
        buf.writeUtf(channelName, MAX_CHANNEL_CHARS);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }

    public static <T> T decode(FriendlyByteBuf buf, PayloadFactory<T> factory) {
        String channel = buf.readUtf(MAX_CHANNEL_CHARS);
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        byte[] data = buf.readByteArray(MAX_FRAME_BYTES);
        try {
            return factory.create(channel, index, total, data);
        } catch (IllegalArgumentException e) {
            throw new io.netty.handler.codec.DecoderException(
                    "Malformed LuaTweaker frame: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    public interface PayloadFactory<T> {
        T create(String channelName, int index, int total, byte[] data);
    }
}
