package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds/merges the Roblox {@code RunService} table. The runtime role is evaluated per call:
 * a client joined to a remote server hosts no server, while single-player hosts an
 * integrated one.
 */
final class ClientRunServiceBinding {

    private static final String RUN_SERVICE = "RunService";

    private ClientRunServiceBinding() {}

    static void register(@NotNull ILuaEngine engine, @NotNull ILuaTable globals,
                         @Nullable ILuaValue signalClass) {
        ILuaValue existingRun = globals.rawget(RUN_SERVICE);
        ILuaTable runService = existingRun != null && existingRun.isTable()
                ? existingRun.asTable() : engine.createTable();
        attachSignals(engine, runService, signalClass);
        runService.rawset("IsClient", args -> engine.wrapBoolean(ClientRuntime.isClient()));
        runService.rawset("IsServer", args -> engine.wrapBoolean(ClientRuntime.isServer()));
        globals.rawset(RUN_SERVICE, runService);
        engine.registerService(RUN_SERVICE, runService);
    }

    private static void attachSignals(@NotNull ILuaEngine engine, @NotNull ILuaTable runService,
                                      @Nullable ILuaValue signalClass) {
        if (signalClass == null || !signalClass.isTable()) {
            return;
        }
        ILuaValue newSignalFn = signalClass.asTable().rawget("new");
        if (newSignalFn == null || newSignalFn.isNil()) {
            return;
        }
        runService.rawset("RenderStepped", engine.callFunction(newSignalFn, signalClass));
        runService.rawset("Heartbeat", engine.callFunction(newSignalFn, signalClass));
        ILuaValue stepped = runService.rawget("Stepped");
        if (stepped == null || stepped.isNil()) {
            runService.rawset("Stepped", engine.callFunction(newSignalFn, signalClass));
        }
    }

    /** Process-role helpers shared with the other client bindings. */
    static final class ClientRuntime {

        private ClientRuntime() {}

        /** True on a client process (integrated or remote); false on a dedicated server. */
        static boolean isClient() {
            return com.luatweaker.api.pal.Platform.isInitialized()
                    && com.luatweaker.api.pal.Platform.getContent().isClient();
        }

        /**
         * True when this process can mutate the world: a dedicated server, or a client hosting
         * an integrated server. A client joined to a remote server reports false.
         */
        static boolean isServer() {
            if (!com.luatweaker.api.pal.Platform.isInitialized()) {
                // Engine-neutral test doubles keep the historical server-side defaults.
                return true;
            }
            com.luatweaker.api.pal.IPlatformContent content =
                    com.luatweaker.api.pal.Platform.getContent();
            return content.isDedicatedServer() || content.hasLocalServer();
        }
    }
}
