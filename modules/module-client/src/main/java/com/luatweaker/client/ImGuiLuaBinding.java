package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the {@code ImGui} service. The service instance is created once per
 * engine in {@code LuaServiceBootstrap}; render-thread state (frame open,
 * window depth, deferred item draws) lives in the platform bridge.
 *
 * <p>Also publishes the {@code ImGui.OnFrame} signal: the platform fires it
 * synchronously once per render tick with an active ImGui frame. All widgets
 * must run inside it — the platform throws a named error otherwise.</p>
 */
public final class ImGuiLuaBinding {

    private ImGuiLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ImGuiServiceImpl imguiService) {
        ILuaTable imguiTable = LuaBinder.bind(engine, "ImGui", imguiService, com.luatweaker.api.client.IImGuiService.class);
        StubCatalog.register("ImGui", com.luatweaker.api.client.IImGuiService.class);

        // The OnFrame signal only makes sense where a frame can exist. Mirror
        // the GuiService.OnRenderHUD pattern: engine-neutral tests keep a
        // signal-less table instead of a per-frame hook that can never fire.
        boolean hasHudRenderer = !com.luatweaker.api.pal.Platform.isInitialized()
                || !com.luatweaker.api.pal.Platform.getContent().isDedicatedServer();
        if (hasHudRenderer) {
            attachOnFrameSignal(engine, imguiTable);
        }
        engine.registerService("ImGui", imguiTable);
    }

    private static void attachOnFrameSignal(@NotNull ILuaEngine engine, @NotNull ILuaTable imguiTable) {
        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        if (signalClass == null || !signalClass.isTable()) {
            return;
        }
        ILuaValue newSignalFn = signalClass.asTable().rawget("new");
        if (newSignalFn != null && newSignalFn.isFunction()) {
            imguiTable.rawset("OnFrame", engine.callFunction(newSignalFn, signalClass));
        }
    }
}
