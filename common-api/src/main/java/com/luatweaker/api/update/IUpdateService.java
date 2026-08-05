package com.luatweaker.api.update;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Update Checker Service - declarative update checking for LuaMods.
 *
 * <p>The engine downloads the {@code update_url} declared in each mod's
 * manifest.json on the Java side (never inside the Lua sandbox) and caches
 * the result. This service only exposes the cached result to Lua - it is a
 * read-only view over engine-owned data, so a script can never use it to
 * reach the network itself.</p>
 */
@LuaDoc(description = "Update Checker Service - read-only update status for LuaMods, fetched by the engine outside the Lua sandbox.")
public interface IUpdateService {

    @LuaDoc(
        description = "Returns the cached update status for a mod id as a table {HasUpdate, LatestVersion, CurrentVersion, UpdateUrl, DownloadUrl, Changelog, Checking, Error}, or nil if the mod is not known to the checker.",
        params = {"modId: string"},
        returnType = "table|nil"
    )
    ILuaValue GetStatus(String modId);

    @LuaDoc(
        description = "Returns a table of all cached statuses that have an available update, keyed by mod id (same shape as GetStatus entries).",
        returnType = "table"
    )
    ILuaValue GetUpdates();
}
