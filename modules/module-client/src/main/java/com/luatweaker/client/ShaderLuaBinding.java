package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the {@code Shaders} service. The service instance is created once per
 * engine in {@code LuaServiceBootstrap}; render-thread state lives in the
 * implementation's static volatiles so reloads keep one stable location.
 */
public final class ShaderLuaBinding {

    private ShaderLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ShaderServiceImpl shaderService) {
        LuaBinder.bind(engine, "Shaders", shaderService, com.luatweaker.api.client.IShaderService.class);
        StubCatalog.register("Shaders", com.luatweaker.api.client.IShaderService.class);
    }
}
