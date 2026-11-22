package com.luatweaker.common.server;

import com.luatweaker.api.server.IServerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.luatweaker.common.CommonServerHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** NeoForge-backed {@link IServerService}. */
public final class ServerServiceImpl implements IServerService {

    private static final int FULL_TPS = 20;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final double NO_TPS = 0.0;

    @Override
    public int getPlayerCount() {
        MinecraftServer server = CommonServerHolder.get();
        return server == null ? 0 : server.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        MinecraftServer server = CommonServerHolder.get();
        return server == null ? 0 : server.getMaxPlayers();
    }

    @Override
    public double getTps() {
        double mspt = getMspt();
        if (mspt <= NO_TPS) {
            return NO_TPS;
        }
        return Math.min(FULL_TPS, MILLIS_PER_SECOND / mspt);
    }

    @Override
    public double getMspt() {
        MinecraftServer server = CommonServerHolder.get();
        return server == null ? 0.0 : server.getAverageTickTimeNanos() / NANOS_PER_MILLI;
    }

    @Override
    public long getTickCount() {
        MinecraftServer server = CommonServerHolder.get();
        return server == null ? 0L : server.getTickCount();
    }

    @Override
    public void broadcast(@NotNull String message) {
        if (message == null) {
            throw new IllegalArgumentException("Server:Broadcast requires a message");
        }
        requireServer().getPlayerList()
                .broadcastSystemMessage(Component.literal(message), false);
    }

    @Override
    public boolean kick(@NotNull String playerNameOrUuid, @NotNull String reason) {
        if (playerNameOrUuid == null || playerNameOrUuid.isBlank() || reason == null) {
            throw new IllegalArgumentException("Server:Kick requires a player name/UUID and a reason");
        }
        MinecraftServer server = requireServer();
        ServerPlayer player = findPlayer(server, playerNameOrUuid);
        if (player == null) {
            return false;
        }
        player.connection.disconnect(Component.literal(reason));
        return true;
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String nameOrUuid) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException notUuid) {
            return server.getPlayerList().getPlayerByName(nameOrUuid);
        }
    }

    private static MinecraftServer requireServer() {
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) {
            throw new IllegalStateException("No server is running");
        }
        return server;
    }
}
