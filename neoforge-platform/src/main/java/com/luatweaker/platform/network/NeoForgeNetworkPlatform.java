package com.luatweaker.platform.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformNetwork;
import com.luatweaker.network.ChunkAssembler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * NeoForge transport: turns one logical message into one or more {@link LuaTweakerPayload}
 * frames and hands them to {@link PacketDistributor}.
 *
 * <p>No protocol knowledge lives here. Chunking happens before the platform call so an
 * oversized message never reaches the vanilla encoder, which would reject it and tear the
 * connection down.</p>
 */
public class NeoForgeNetworkPlatform implements IPlatformNetwork {
    private static final int CHUNKED_FRAME_THRESHOLD = 1;

    @Override
    public void sendPayloadBytes(String playerUuid, String channelName, byte[] data) {
        ServerPlayer player = resolvePlayer(playerUuid, channelName);
        if (player == null) {
            return;
        }
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> CLIENT] '" + channelName + "' to " + playerUuid);
        sendFrames(channelName, data, payload -> PacketDistributor.sendToPlayer(player, payload));
    }

    @Override
    public void broadcastPayloadBytes(String channelName, byte[] data) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> ALL CLIENTS] '" + channelName + "'");
        sendFrames(channelName, data, PacketDistributor::sendToAllPlayers);
    }

    @Override
    public void sendPayloadBytesToServer(String channelName, byte[] data) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [CLIENT -> SERVER] '" + channelName + "'");
        sendFrames(channelName, data, PacketDistributor::sendToServer);
    }

    private static void sendFrames(@NotNull String channelName, byte @Nullable [] data,
                                   @NotNull Consumer<LuaTweakerPayload> sender) {
        if (data == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped '" + channelName + "': null payload");
            return;
        }
        List<byte[]> chunks;
        try {
            chunks = ChunkAssembler.split(data);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped '" + channelName + "': " + e.getMessage());
            return;
        }
        int total = chunks.size();
        if (total > CHUNKED_FRAME_THRESHOLD) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network] Split '" + channelName + "' into " + total + " frames ("
                            + data.length + " bytes)");
        }
        // Every frame carries its own sequence index and slice, so each one is a distinct
        // message; nothing can be shared between iterations.
        IntStream.range(0, total)
                .mapToObj(index -> new LuaTweakerPayload(channelName, index, total, chunks.get(index)))
                .forEach(sender);
    }

    private static @Nullable ServerPlayer resolvePlayer(@NotNull String playerUuid,
                                                        @NotNull String channelName) {
        return resolvePlayerOn(ServerLifecycleHooks.getCurrentServer(), playerUuid, channelName);
    }

    private static @Nullable ServerPlayer resolvePlayerOn(@Nullable MinecraftServer server,
                                                          @NotNull String playerUuid,
                                                          @NotNull String channelName) {
        if (server == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped server packet '" + channelName + "': no server running");
            return null;
        }
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(playerUuid));
            if (player == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Network] Dropped packet '" + channelName + "': player " + playerUuid + " is offline");
            }
            return player;
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped packet '" + channelName + "': invalid player uuid '"
                            + playerUuid + "'");
            return null;
        }
    }
}
