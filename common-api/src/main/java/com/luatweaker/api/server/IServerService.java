package com.luatweaker.api.server;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import org.jetbrains.annotations.NotNull;

/**
 * Low-level read/control surface for the running dedicated/integrated server.
 *
 * <p>TPS/MSPT are sampled from the server's own tick-time average; they return 0 when no
 * server is running. Broadcast and kick fail loudly when there is no server.</p>
 */
@LuaDoc(description = "Server status and control primitives (player count, TPS/MSPT, broadcast, kick).")
public interface IServerService {

    @LuaDoc(description = "Number of connected players (0 when no server is running).",
            returnType = LuaDocFragments.Types.INTEGER)
    int getPlayerCount();

    @LuaDoc(description = "Configured maximum player count (0 when no server is running).",
            returnType = LuaDocFragments.Types.INTEGER)
    int getMaxPlayers();

    @LuaDoc(description = "Average ticks per second (0 when no server is running; capped at 20).",
            returnType = LuaDocFragments.Types.NUMBER)
    double getTps();

    @LuaDoc(description = "Average milliseconds per tick (0 when no server is running).",
            returnType = LuaDocFragments.Types.NUMBER)
    double getMspt();

    @LuaDoc(description = "Server tick counter (0 when no server is running).",
            returnType = LuaDocFragments.Types.INTEGER)
    long getTickCount();

    @LuaDoc(description = "Broadcasts a system message to every connected player.",
            params = {"message: string"})
    void broadcast(@NotNull String message);

    @LuaDoc(description = "Kicks a player by name or UUID. Returns false when the player is not online.",
            params = {"playerNameOrUuid: string", "[reason: string]"},
            returnType = LuaDocFragments.Types.BOOLEAN)
    boolean kick(@NotNull String playerNameOrUuid, @LuaDefault("Disconnected") @NotNull String reason);
}
