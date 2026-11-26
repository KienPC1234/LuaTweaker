package com.luatweaker.common.server;

import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.common.CommonServerHolder;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server lookup helpers shared by {@link ServerServiceImpl}. */
final class ServerOps {

    private ServerOps() {
    }

    @NotNull
    static MinecraftServer requireServer() {
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) {
            throw new IllegalStateException("No server is running");
        }
        return server;
    }

    @Nullable
    static ServerPlayer findPlayer(@NotNull MinecraftServer server, @NotNull String nameOrUuid) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException notUuid) {
            return server.getPlayerList().getPlayerByName(nameOrUuid);
        }
    }

    @NotNull
    static List<IPlayer> getOnlinePlayers() {
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) {
            return List.of();
        }
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        List<IPlayer> out = new ArrayList<>(online.size());
        for (ServerPlayer player : online) {
            out.add(new MinecraftPlayerWrapper(player));
        }
        return List.copyOf(out);
    }
}
