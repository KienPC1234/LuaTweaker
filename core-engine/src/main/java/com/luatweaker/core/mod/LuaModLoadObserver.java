package com.luatweaker.core.mod;

/**
 * Progress sink for {@link LuaModManager#loadLuaMods}.
 *
 * <p>Implemented by the platform layer so LuaMod loading shows up on the FML early loading
 * window. core-engine must not import {@code net.neoforged.*}, so the manager reports plain
 * text events and the platform decides how (or whether) to display them.</p>
 */
public interface LuaModLoadObserver {

    /** No-op observer for callers that do not display progress (for example /lt reload). */
    LuaModLoadObserver NONE = new LuaModLoadObserver() { };

    /**
     * @param index 1-based position in the dependency-sorted load order
     * @param total discovered mod count, so a UI can render "index/total"
     * @param modId manifest id of the mod that is about to execute its entrypoint
     */
    default void onModStart(int index, int total, String modId) { }

    /**
     * @param loadedMods mods that actually loaded; skipped and failed entries are excluded
     * @param total      discovered mod count
     */
    default void onLoadComplete(int loadedMods, int total) { }
}
