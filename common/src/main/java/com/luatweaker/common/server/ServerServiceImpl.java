package com.luatweaker.common.server;

import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.server.IServerService;
import com.luatweaker.common.CommonServerHolder;
import com.luatweaker.common.interaction.WorldBorderOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Architectury common {@link IServerService}. */
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
        ServerOps.requireServer().getPlayerList()
                .broadcastSystemMessage(Component.literal(message), false);
    }

    @Override
    public boolean kick(@NotNull String playerNameOrUuid, @NotNull String reason) {
        if (playerNameOrUuid == null || playerNameOrUuid.isBlank() || reason == null) {
            throw new IllegalArgumentException("Server:Kick requires a player name/UUID and a reason");
        }
        MinecraftServer server = ServerOps.requireServer();
        ServerPlayer player = ServerOps.findPlayer(server, playerNameOrUuid);
        if (player == null) {
            return false;
        }
        player.connection.disconnect(Component.literal(reason));
        return true;
    }

    @Override
    @NotNull
    public List<IPlayer> getPlayers() {
        return ServerOps.getOnlinePlayers();
    }

    @Override
    @Nullable
    public Map<String, Object> getWorldBorder(@NotNull String dimension) {
        return WorldBorderOps.getWorldBorder(dimension);
    }

    @Override
    public void setWorldBorder(double centerX, double centerZ, double size,
                               long lerpMillis, @NotNull String dimension) {
        WorldBorderOps.applyWorldBorder(dimension, centerX, centerZ, size, lerpMillis);
    }
}
