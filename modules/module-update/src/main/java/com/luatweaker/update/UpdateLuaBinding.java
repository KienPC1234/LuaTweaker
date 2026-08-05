package com.luatweaker.update;

import com.luatweaker.api.update.IUpdateService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.web.IWebService;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the Update Checker (read-only, default for every mod) and the Web
 * Service (permission-gated HTTP GET) into the Lua engine.
 */
public final class UpdateLuaBinding {
    private UpdateLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine,
                                        @NotNull IUpdateService updateService,
                                        @NotNull IWebService webService) {
        LuaBinder.bind(engine, "Update", updateService, IUpdateService.class);
        LuaBinder.bind(engine, "Net", webService, IWebService.class);
        engine.registerService("UpdateServiceImpl", updateService);
        engine.registerService("WebServiceImpl", webService);
    }
}
