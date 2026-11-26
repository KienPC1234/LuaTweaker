package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;

import java.util.List;

@LuaDoc(description = "Service for dynamically registering key bindings in Minecraft Controls menu.")
public interface IKeyBindService {

    record KeyBindEntry(
            String id,
            String displayName,
            String category,
            int defaultKey,
            String onPressPayload,
            String inputType,
            String modifier
    ) {}

    @LuaDoc(
        description = "Registers a new key binding in the client Controls menu.",
        params = {LuaDocFragments.Common.ID_STRING, "displayName: string", "category: string", "defaultKey: number", "onPressPayload: string"}
    )
    void registerKeyBind(String id, String displayName, String category, int defaultKey, String onPressPayload);

    @LuaDoc(
        description = "Registers a key binding with input type and modifier. "
            + "inputType: 'key' (keyboard keysym, default) or 'mouse' (GLFW button code). "
            + "modifier: 'none' (default), 'shift', 'ctrl' or 'alt' — the extra key "
            + "that must be held for the press to fire. Unknown values throw.",
        params = {LuaDocFragments.Common.ID_STRING, "displayName: string", "category: string", "defaultKey: number",
                  "onPressPayload: string", "inputType: string", "modifier: string"}
    )
    void registerKeyBind(String id, String displayName, String category, int defaultKey,
                         String onPressPayload, String inputType, String modifier);

    @LuaDoc(
        description = "Returns all dynamically registered key bindings.",
        params = {},
        returnType = "List<KeyBindEntry>"
    )
    List<KeyBindEntry> getRegisteredKeyBinds();

    @LuaDoc(
        description = "Triggers a key binding activation event on the client.",
        params = {LuaDocFragments.Common.ID_STRING, "payload: string"}
    )
    void triggerKeyBind(String id, String payload);

    @LuaDoc(
        description = "Triggers a key binding release event on the client.",
        params = {LuaDocFragments.Common.ID_STRING, "payload: string"}
    )
    void triggerKeyBindRelease(String id, String payload);

    void setKeyBindListener(java.util.function.BiConsumer<String, String> listener);

    void setKeyBindReleaseListener(java.util.function.BiConsumer<String, String> listener);

    /**
     * Monotonically incrementing version bumped on each keybind registration so
     * client tick polling can skip scanning {@link #getRegisteredKeyBinds()} when unchanged.
     */
    default long getRegistrationVersion() {
        return -1L;
    }
}
