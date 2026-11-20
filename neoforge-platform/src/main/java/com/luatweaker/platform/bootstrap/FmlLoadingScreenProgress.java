package com.luatweaker.platform.bootstrap;

import com.luatweaker.core.mod.LuaModLoadObserver;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * Mirrors LuaMod loading onto the FML early loading window, next to "Loading mods...".
 *
 * <p>{@link StartupNotificationManager#addModMessage(String)} renders a line in the loading
 * window's message log. FML keeps only ASCII characters and truncates each line to 80
 * characters, so ids with other characters are stripped by FML itself. No progress bar is
 * added: the early display renders at most two bars and FML's own mod-loading tasks already
 * occupy them, so a third bar would never become visible.</p>
 */
public final class FmlLoadingScreenProgress implements LuaModLoadObserver {

    private static final String PREFIX = "[LuaTweaker] ";

    @Override
    public void onModStart(int index, int total, String modId) {
        StartupNotificationManager.addModMessage(PREFIX + modId + " (" + index + "/" + total + ")");
    }

    @Override
    public void onLoadComplete(int loadedMods, int total) {
        StartupNotificationManager.addModMessage(PREFIX + "LuaMods ready: " + loadedMods + "/" + total);
    }
}
