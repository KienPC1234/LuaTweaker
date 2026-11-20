package com.luatweaker.platform.network;

import com.luatweaker.network.ChunkAssembler;
import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One frame of the LuaTweaker wire protocol.
 *
 * <p>Every logical message (RemoteEvent, RemoteFunction envelope, client control command)
 * travels through this single payload type. Messages larger than {@link ChunkAssembler#CHUNK_BYTES}
 * are split into {@code total} frames and reassembled by {@link ChunkAssembler} before the
 * Lua layer sees them; a connection is never torn down by an oversized payload.</p>
 *
 * <p>{@code data} is binary for Lua traffic and UTF-8 JSON for the {@code LuaTweaker:}
 * control channels.</p>
 */
public record LuaTweakerPayload(String channelName, int index, int total, byte[] data)
        implements CustomPacketPayload {

    /** Longest accepted channel name; Lua event names are plain identifiers in practice. */
    public static final int MAX_CHANNEL_CHARS = 128;
    /** Defensive bound on the declared frame size, matching the sender's chunk size. */
    public static final int MAX_FRAME_BYTES = ChunkAssembler.CHUNK_BYTES;

    public static final CustomPacketPayload.Type<LuaTweakerPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LuaTweakerMod.MODID, "packet"));

    public static final StreamCodec<FriendlyByteBuf, LuaTweakerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.channelName(), MAX_CHANNEL_CHARS);
                        buf.writeVarInt(payload.index());
                        buf.writeVarInt(payload.total());
                        buf.writeByteArray(payload.data());
                    },
                    buf -> decodeFrame(buf));

    private static LuaTweakerPayload decodeFrame(FriendlyByteBuf buf) {
        String channel = buf.readUtf(MAX_CHANNEL_CHARS);
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        byte[] data = buf.readByteArray(MAX_FRAME_BYTES);
        try {
            return new LuaTweakerPayload(channel, index, total, data);
        } catch (IllegalArgumentException e) {
            // A forged or corrupt frame violates the protocol, so drop the connection
            // through the normal decoder path instead of leaking an unchecked exception.
            throw new io.netty.handler.codec.DecoderException(
                    "Malformed LuaTweaker frame: " + e.getMessage(), e);
        }
    }

    public LuaTweakerPayload {
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("Network frame channel must not be empty");
        }
        if (total < 1) {
            throw new IllegalArgumentException("Network frame total must be at least 1, got " + total);
        }
        if (index < 0 || index >= total) {
            throw new IllegalArgumentException("Network frame index " + index + " is outside 0.." + (total - 1));
        }
        if (data == null) {
            throw new IllegalArgumentException("Network frame data must not be null");
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
