package com.luatweaker.api.modsync;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import org.jetbrains.annotations.NotNull;

/**
 * Low-level mod-sync primitives: server lists its LuaMods, clients report theirs,
 * Lua decides extra conditions (kick, warn) on top of the engine handshake.
 */
@LuaDoc(description = "LuaMod client/server sync primitives (required mods, version reports, enforcement).")
public interface IModSyncService {

    @LuaDoc(description = "Server-side required LuaMods (id to version), from loaded universal required mods.",
            returnType = "table")
    @NotNull String getServerModsJson();

    @LuaDoc(description = "Last reported client mods for a player (JSON object id to version). Empty when unknown.",
            params = {"playerUuid: string"},
            returnType = LuaDocFragments.Types.STRING)
    @NotNull String getClientModsJson(@NotNull String playerUuid);

    @LuaDoc(description = "Compares a player client mods against the server (missing, mismatch). Returns JSON report.",
            params = {"playerUuid: string"},
            returnType = LuaDocFragments.Types.STRING)
    @NotNull String checkPlayer(@NotNull String playerUuid);

    @LuaDoc(description = "Current enforcement mode: kick or warn.",
            returnType = LuaDocFragments.Types.STRING)
    @NotNull String getEnforcementMode();

    @LuaDoc(description = "Server-advertised mod bundle on the server, last verdict offer on the client "
            + "(JSON {id,url,sha256,version,name,filename}). Empty when no server offers a bundle. "
            + "Read-only: Lua can build a custom download UI on top, but consent and "
            + "verification stay on the Java side.",
            returnType = LuaDocFragments.Types.STRING)
    @NotNull String getBundleInfoJson();

    @LuaDoc(description = "Overrides enforcement for this session (kick or warn).",
            params = {"mode: string"})
    void setEnforcementMode(@NotNull String mode);
}
