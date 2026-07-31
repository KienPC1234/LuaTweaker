package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;

import java.util.List;

@LuaDoc(description = "Service for dynamically registering key bindings in Minecraft Controls menu.")
public interface IKeyBindService {

    record KeyBindEntry(
            String id,
            String displayName,
            String category,
            int defaultKey,
            String onPressPayload
    ) {}

    @LuaDoc(
        description = "Registers a new key binding in the client Controls menu.",
        params = {"id: string", "displayName: string", "category: string", "defaultKey: number", "onPressPayload: string"},
        returnType = "void"
    )
    void registerKeyBind(String id, String displayName, String category, int defaultKey, String onPressPayload);

    @LuaDoc(
        description = "Returns all dynamically registered key bindings.",
        params = {},
        returnType = "List<KeyBindEntry>"
    )
    List<KeyBindEntry> getRegisteredKeyBinds();

    @LuaDoc(
        description = "Triggers a key binding activation event on the client.",
        params = {"id: string", "payload: string"},
        returnType = "void"
    )
    void triggerKeyBind(String id, String payload);

    void setKeyBindListener(java.util.function.BiConsumer<String, String> listener);
}
