package com.luatweaker.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server wire frame for LuaTweaker network traffic.
 */
public record LuaTweakerC2SPayload(String channelName, int index, int total, byte[] data)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LuaTweakerC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("luatweaker", "c2s_packet"));

    public static final StreamCodec<FriendlyByteBuf, LuaTweakerC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> LuaTweakerPayloadHelper.encode(
                            buf, payload.channelName(), payload.index(), payload.total(), payload.data()),
                    buf -> LuaTweakerPayloadHelper.decode(buf, LuaTweakerC2SPayload::new));

    public LuaTweakerC2SPayload {
        LuaTweakerPayloadHelper.validate(channelName, index, total, data);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
