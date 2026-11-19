package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * Callback invoked when a player destroys a block using this item.
 */
@FunctionalInterface
@LuaDoc(description = "Callback executed when a player breaks a block using this item.")
public interface ItemMineHandler {
    void accept(Object player, Object itemStack, int x, int y, int z);
}
