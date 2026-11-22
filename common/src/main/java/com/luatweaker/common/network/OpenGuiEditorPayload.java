package com.luatweaker.common.network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record OpenGuiEditorPayload(String title, int guiWidth, int guiHeight, boolean isJeiMode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenGuiEditorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("luatweaker", "open_gui_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenGuiEditorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.title(), 64);
                        buf.writeVarInt(payload.guiWidth());
                        buf.writeVarInt(payload.guiHeight());
                        buf.writeBoolean(payload.isJeiMode());
                    },
                    buf -> new OpenGuiEditorPayload(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readBoolean())
            );

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
