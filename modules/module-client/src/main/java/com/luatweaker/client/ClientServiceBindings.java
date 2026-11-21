package com.luatweaker.client;

import com.luatweaker.api.client.ICameraService;
import com.luatweaker.api.client.IClientEffectsService;
import com.luatweaker.api.client.ITweenService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the client services that have no custom table shape: Camera, ClientEffects and
 * TweenService. All three degrade through {@code Platform.getClientOrNull()} when no client
 * process exists, so the Lua surface is identical on every distribution.
 */
final class ClientServiceBindings {

    private ClientServiceBindings() {}

    static void register(@NotNull ILuaEngine engine) {
        LuaBinder.bind(engine, "Camera", new CameraServiceImpl(), ICameraService.class);
        StubCatalog.register("Camera", ICameraService.class);
        LuaBinder.bind(engine, "ClientEffects", new ClientEffectsServiceImpl(), IClientEffectsService.class);
        StubCatalog.register("ClientEffects", IClientEffectsService.class);
        LuaBinder.bind(engine, "TweenService", new TweenServiceImpl(engine), ITweenService.class);
        StubCatalog.register("TweenService", ITweenService.class);
    }
}
