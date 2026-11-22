package com.luatweaker.neoforge.bootstrap;

import com.luatweaker.core.mod.LuaModLoadObserver;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

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
