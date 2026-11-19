package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * Callback invoked every tick while a player holds right-click to use/charge an item.
 */
@FunctionalInterface
@LuaDoc(description = "Callback executed each tick while an item is being actively used/charged.")
public interface ItemUseTickHandler {
    void accept(Object player, Object itemStack, int ticksRemaining);
}
