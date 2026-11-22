package com.luatweaker.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client wire frame for LuaTweaker network traffic.
 */
public record LuaTweakerS2CPayload(String channelName, int index, int total, byte[] data)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LuaTweakerS2CPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("luatweaker", "s2c_packet"));

    public static final StreamCodec<FriendlyByteBuf, LuaTweakerS2CPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> LuaTweakerPayloadHelper.encode(
                            buf, payload.channelName(), payload.index(), payload.total(), payload.data()),
                    buf -> LuaTweakerPayloadHelper.decode(buf, LuaTweakerS2CPayload::new));

    public LuaTweakerS2CPayload {
        LuaTweakerPayloadHelper.validate(channelName, index, total, data);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
