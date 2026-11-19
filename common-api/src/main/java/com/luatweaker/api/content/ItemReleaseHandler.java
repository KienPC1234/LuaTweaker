package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * Callback invoked when a player releases right-click after charging/using an item.
 */
@FunctionalInterface
@LuaDoc(description = "Callback executed when a player releases the use/charge button before or at completion.")
public interface ItemReleaseHandler {
    void accept(Object player, Object itemStack, int timeLeft);
}
