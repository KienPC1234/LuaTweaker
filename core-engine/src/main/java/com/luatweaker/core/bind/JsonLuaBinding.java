package com.luatweaker.core.bind;

import com.luatweaker.api.json.IJsonService;
import com.luatweaker.api.vm.ILuaEngine;
import org.jetbrains.annotations.NotNull;

/**
 * Registers bindings for the low-level {@link IJsonService}.
 */
public final class JsonLuaBinding {

    private JsonLuaBinding() {}

    /**
     * Binds the JSON service to the engine as both "Json" and "json".
     */
    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull IJsonService service) {
        LuaBinder.bind(engine, "Json", service, IJsonService.class, "json");
    }
}
