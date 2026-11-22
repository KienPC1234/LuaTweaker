package com.luatweaker.common.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformNetwork;
import com.luatweaker.network.ChunkAssembler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Architectury transport: turns one logical message into one or more {@link LuaTweakerC2SPayload}
 * or {@link LuaTweakerS2CPayload} frames and hands them to {@link NetworkManager}.
 *
 * <p>No protocol knowledge lives here. Chunking happens before the platform call so an
 * oversized message never reaches the vanilla encoder, which would reject it and tear the
 * connection down.</p>
 */
public class MinecraftNetworkPlatform implements IPlatformNetwork {
    private static final int CHUNKED_FRAME_THRESHOLD = 1;
    private static final String DROP_PREFIX = "[Network] Dropped '";

    @Override
    public void sendPayloadBytes(String playerUuid, String channelName, byte[] data) {
        ServerPlayer player = resolvePlayer(playerUuid, channelName);
        if (player == null) {
            return;
        }
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> CLIENT] '" + channelName + "' to " + playerUuid);
        sendS2CFrames(channelName, data, payload -> NetworkManager.sendToPlayer(player, payload));
    }

    @Override
    public void broadcastPayloadBytes(String channelName, byte[] data) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> ALL CLIENTS] '" + channelName + "'");
        sendS2CFrames(channelName, data, payload -> {
            MinecraftServer s = com.luatweaker.common.CommonServerHolder.get();
            if (s != null) {
                NetworkManager.sendToPlayers(s.getPlayerList().getPlayers(), payload);
            }
        });
    }

    @Override
    public void sendPayloadBytesToPlayers(List<String> playerUuids, String channelName, byte[] data) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return;
        }
        MinecraftServer server = com.luatweaker.common.CommonServerHolder.get();
        if (server == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped server packet '" + channelName + "': no server running");
            return;
        }
        List<LuaTweakerS2CPayload> frames = splitS2CFrames(channelName, data);
        if (frames.isEmpty()) {
            return;
        }
        int sent = sendFramesToPlayers(server, playerUuids, channelName, frames);
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> " + sent + " CLIENTS] '" + channelName + "'");
    }

    private static List<LuaTweakerS2CPayload> splitS2CFrames(@NotNull String channelName, byte @Nullable [] data) {
        if (data == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    DROP_PREFIX + channelName + "': null payload");
            return List.of();
        }
        try {
            List<byte[]> chunks = ChunkAssembler.split(data);
            int total = chunks.size();
            return IntStream.range(0, total)
                    .mapToObj(index -> new LuaTweakerS2CPayload(channelName, index, total, chunks.get(index)))
                    .toList();
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    DROP_PREFIX + channelName + "': " + e.getMessage());
            return List.of();
        }
    }

    private static int sendFramesToPlayers(@NotNull MinecraftServer server,
                                           @NotNull List<String> playerUuids,
                                           @NotNull String channelName,
                                           @NotNull List<LuaTweakerS2CPayload> frames) {
        int sent = 0;
        for (String uuid : playerUuids) {
            if (uuid == null || uuid.isBlank()) {
                continue;
            }
            ServerPlayer player = resolvePlayerOn(server, uuid, channelName);
            if (player == null) {
                continue;
            }
            for (LuaTweakerS2CPayload frame : frames) {
                NetworkManager.sendToPlayer(player, frame);
            }
            sent++;
        }
        return sent;
    }

    @Override
    public void sendPayloadBytesToServer(String channelName, byte[] data) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [CLIENT -> SERVER] '" + channelName + "'");
        sendC2SFrames(channelName, data, NetworkManager::sendToServer);
    }

    private static void sendC2SFrames(@NotNull String channelName, byte @Nullable [] data,
                                      @NotNull Consumer<LuaTweakerC2SPayload> sender) {
        if (data == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    DROP_PREFIX + channelName + "': null payload");
            return;
        }
        List<byte[]> chunks;
        try {
            chunks = ChunkAssembler.split(data);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    DROP_PREFIX + channelName + "': " + e.getMessage());
            return;
        }
        int total = chunks.size();
        if (total > CHUNKED_FRAME_THRESHOLD) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network] Split '" + channelName + "' into " + total + " frames ("
                            + data.length + " bytes)");
        }
        IntStream.range(0, total)
                .mapToObj(index -> new LuaTweakerC2SPayload(channelName, index, total, chunks.get(index)))
                .forEach(sender);
    }

    private static void sendS2CFrames(@NotNull String channelName, byte @Nullable [] data,
                                      @NotNull Consumer<LuaTweakerS2CPayload> sender) {
        if (data == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    DROP_PREFIX + channelName + "': null payload");
            return;
        }
        List<byte[]> chunks;
        try {
            chunks = ChunkAssembler.split(data);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    DROP_PREFIX + channelName + "': " + e.getMessage());
            return;
        }
        int total = chunks.size();
        if (total > CHUNKED_FRAME_THRESHOLD) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network] Split '" + channelName + "' into " + total + " frames ("
                            + data.length + " bytes)");
        }
        IntStream.range(0, total)
                .mapToObj(index -> new LuaTweakerS2CPayload(channelName, index, total, chunks.get(index)))
                .forEach(sender);
    }

    private static @Nullable ServerPlayer resolvePlayer(@NotNull String playerUuid,
                                                        @NotNull String channelName) {
        return resolvePlayerOn(com.luatweaker.common.CommonServerHolder.get(), playerUuid, channelName);
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
