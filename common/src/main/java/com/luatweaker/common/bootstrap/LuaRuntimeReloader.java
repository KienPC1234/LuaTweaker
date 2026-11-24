package com.luatweaker.common.bootstrap;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.command.CommandServiceImpl;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.config.LuaTweakerConfig;
import com.luatweaker.common.content.ContentVirtualFiles;
import com.luatweaker.common.datapack.DatapackMaterializer;
import com.luatweaker.common.integration.IntegrationManager;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.recipe.InterceptionHelper;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.mod.LuaModLoadObserver;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.events.EventServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates startup and hot-reload execution of the Lua runtime engine,
 * virtual datapack materialization, and EmmyLua stub export.
 */
public final class LuaRuntimeReloader {

    /**
     * Intentional static hook allowing platform entrypoints (e.g. NeoForge FML startup progress)
     * to observe mod loading without coupling {@code common} to loader classes.
     */
    private static final AtomicReference<LuaModLoadObserver> LOAD_OBSERVER =
            new AtomicReference<>(LuaModLoadObserver.NONE);

    private LuaRuntimeReloader() {
    }

    public static void setLoadObserver(@NotNull LuaModLoadObserver observer) {
        LOAD_OBSERVER.set(observer);
    }

    @NotNull
    public static ILuaEngine loadOrReload(@NotNull File gameDir,
                                          @NotNull File luaDir,
                                          boolean isClient,
                                          @NotNull ContentServiceImpl contentService,
                                          @NotNull DatapackServiceImpl datapackService,
                                          boolean isStartup) {
        LuaTweakerConfig.load();
        com.luatweaker.common.modsync.ServerModSync.syncConfig();
        if (!isStartup) {
            LuaTweakerLog.get().info(LogStage.SYSTEM, "=== BEGIN RELOAD ===");
            teardownPreviousEngine(LuaTweakerCommon.getActiveEngine());
        }

        CommandServiceImpl.clear();
        datapackService.clear();
        InterceptionHelper.clearPending();

        StorageServiceImpl storageService = new StorageServiceImpl(new File(luaDir, "storage.json"));
        CommonRecipeManager recipeManager = new CommonRecipeManager(datapackService);

        ILuaEngine engine = LuaEngineFactory.create(
                LuaTweakerConfig.isDebug(), LuaTweakerConfig.toEngineLimits());
        engine.setLuaDirectory(luaDir);
        LuaServiceBootstrap.registerAllServices(
                engine, contentService, storageService, datapackService, recipeManager);

        String dist = isClient ? "universal" : "server";
        LuaModLoadObserver observer = isStartup ? LOAD_OBSERVER.get() : LuaModLoadObserver.NONE;
        LuaModManager.loadLuaMods(luaDir, engine, dist, observer);

        LuaTweakerCommon.setActiveEngine(engine);
        IntegrationManager.initEngine(engine);

        if (!isStartup) {
            finalizeDatapackAndStubs(gameDir, luaDir, contentService, datapackService);
            LuaTweakerLog.get().info(LogStage.SYSTEM, "=== END RELOAD ===");
        }
        return engine;
    }

    public static void finalizeDatapackAndStubs(@NotNull File gameDir,
                                                @NotNull File luaDir,
                                                @NotNull ContentServiceImpl contentService,
                                                @NotNull DatapackServiceImpl datapackService) {
        ContentVirtualFiles.rebuild(luaDir, contentService, datapackService);
        DatapackMaterializer.applyAll(luaDir, contentService, datapackService);
        LuaStubSynchronizer.exportIfEnabled(gameDir);
    }

    private static void teardownPreviousEngine(@Nullable ILuaEngine previous) {
        if (previous != null) {
            LuaModManager.fireModTeardownHooks(previous);
        }
        EventServiceImpl.fireTeardownHooks();
        if (previous != null) {
            previous.shutdown();
        }
    }
}
