package com.luatweaker.platform.network;

import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ItemAnimationPayload(int entityId, String animationName, int durationTicks, float speed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ItemAnimationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LuaTweakerMod.MODID, "item_animation"));

    public static final StreamCodec<FriendlyByteBuf, ItemAnimationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ItemAnimationPayload::entityId,
            ByteBufCodecs.STRING_UTF8, ItemAnimationPayload::animationName,
            ByteBufCodecs.VAR_INT, ItemAnimationPayload::durationTicks,
            ByteBufCodecs.FLOAT, ItemAnimationPayload::speed,
            ItemAnimationPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
