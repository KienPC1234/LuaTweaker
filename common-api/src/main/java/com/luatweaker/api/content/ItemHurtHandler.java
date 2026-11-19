package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * Callback invoked when a player attacks an entity with this item.
 */
@FunctionalInterface
@LuaDoc(description = "Callback executed when a player attacks/hits an entity with this item.")
public interface ItemHurtHandler {
    void accept(Object targetEntity, Object attackerPlayer, Object itemStack);
}
