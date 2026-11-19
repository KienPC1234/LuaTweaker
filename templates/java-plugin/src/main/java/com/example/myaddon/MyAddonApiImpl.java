package com.example.myaddon;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal thread-safe implementation. Business logic lives in Lua, not here. */
public final class MyAddonApiImpl implements IMyAddonApi {
    private final Map<String, String> markers = new ConcurrentHashMap<>();

    @Override
    public void putMarker(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("putMarker requires argument 1 (key)");
        }
        markers.put(key, value);
    }

    @Override
    public @Nullable String getMarker(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("getMarker requires argument 1 (key)");
        }
        return markers.get(key);
    }
}
