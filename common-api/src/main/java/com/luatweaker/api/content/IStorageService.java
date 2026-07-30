package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Service for persistent key-value modpack data storage across server restarts.")
public interface IStorageService {
    @LuaDoc(description = "Saves a value for a given key.", params = {"key: string", "value: any"})
    void set(String key, Object value);

    @LuaDoc(description = "Retrieves a stored value or returns the default fallback.", params = {"key: string", "defaultVal: any"}, returnType = "any")
    Object get(String key, Object defaultVal);

    void save();
    void load();
}
