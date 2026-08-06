package com.luatweaker.api.event;

/**
 * Single source of truth for every Lua event name shared between Java and Lua.
 * Java posters and the Lua-facing {@code Events.Names} table both read from here,
 * so a listener can never silently break on a renamed/typo'd event name.
 */
public final class EventNames {

    /** Fired once for every registered mod before a reload; Lua global cleanup hook. */
    public static final String ON_SCRIPT_UNLOAD = "OnScriptUnload";

    /** Container was right-clicked open; payload: Id, X, Y, Z, Player. */
    public static final String CONTAINER_OPENED = "ContainerOpened";

    /** Container rule rejected an item; payload: Id, X, Y, Z, ItemId, Count, Slot. */
    public static final String CONTAINER_ITEM_REJECTED = "ContainerItemRejected";

    private EventNames() {}
}
