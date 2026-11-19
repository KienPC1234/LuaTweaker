package com.example.myaddon;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * Single source of truth for the addon API.
 * LuaBinder, EmmyLua stubs and docs are all generated from these annotations.
 * Keep every method a LOW-LEVEL primitive; gameplay logic stays in Lua.
 */
@LuaDoc(description = "Example addon API.")
public interface IMyAddonApi {
    @LuaDoc(
        description = "Stores a marker value under a key.",
        params = {"key: string", "value: string"},
        returnType = "void"
    )
    void putMarker(String key, String value);

    @LuaDoc(
        description = "Reads a marker value, or nil when absent.",
        params = {"key: string"},
        returnType = "string|nil"
    )
    String getMarker(String key);
}
