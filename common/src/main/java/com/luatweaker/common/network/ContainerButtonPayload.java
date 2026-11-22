package com.luatweaker.common.network;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ContainerButtonPayload(BlockPos pos, String buttonId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ContainerButtonPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("luatweaker", "container_button"));

    public static final StreamCodec<FriendlyByteBuf, ContainerButtonPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ContainerButtonPayload::pos,
            ByteBufCodecs.STRING_UTF8, ContainerButtonPayload::buttonId,
            ContainerButtonPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
