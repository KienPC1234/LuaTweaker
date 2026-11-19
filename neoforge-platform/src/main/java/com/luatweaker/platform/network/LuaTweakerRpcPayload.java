package com.luatweaker.platform.network;

import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * RemoteFunction request/response envelope. Kept separate from the RemoteEvent payload so a
 * request is never mistaken for an event channel and vice versa.
 */
public record LuaTweakerRpcPayload(String requestId, String phase, String functionName,
                                   String dataJson, String error) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LuaTweakerRpcPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LuaTweakerMod.MODID, "rpc"));

    public static final StreamCodec<FriendlyByteBuf, LuaTweakerRpcPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, LuaTweakerRpcPayload::requestId,
                    ByteBufCodecs.STRING_UTF8, LuaTweakerRpcPayload::phase,
                    ByteBufCodecs.STRING_UTF8, LuaTweakerRpcPayload::functionName,
                    ByteBufCodecs.stringUtf8(262144), LuaTweakerRpcPayload::dataJson,
                    ByteBufCodecs.stringUtf8(32768), LuaTweakerRpcPayload::error,
                    LuaTweakerRpcPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
