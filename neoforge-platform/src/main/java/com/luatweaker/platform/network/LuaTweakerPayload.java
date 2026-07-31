package com.luatweaker.platform.network;

import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LuaTweakerPayload(String channelName, String dataJson) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LuaTweakerPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LuaTweakerMod.MODID, "packet"));
    
    public static final StreamCodec<FriendlyByteBuf, LuaTweakerPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, LuaTweakerPayload::channelName,
        ByteBufCodecs.STRING_UTF8, LuaTweakerPayload::dataJson,
        LuaTweakerPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
