package com.luatweaker.core.signal;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared synchronous dispatcher for bootstrap {@code Signal} instances
 * ({@code RunService}, {@code GuiService}, {@code ImGui}, {@code UserInputService}, {@code Client}).
 * Caches resolved signal and {@code FireSync} references per engine and short-circuits
 * before argument wrapping when zero listeners are connected.
 */
public final class LuaSignalDispatcher {

    private static final String SIGNAL_CLASS_NAME = "Signal";
    private static final String SIGNAL_FIRE_SYNC = "FireSync";
    private static final String SIGNAL_FIRE = "Fire";
    private static final String LISTENERS_FIELD = "_listeners";

    private record CachedSignal(ILuaTable signal, ILuaValue fireSync) {}

    private static final Map<ILuaEngine, Map<String, Map<String, CachedSignal>>> SIGNAL_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LuaSignalDispatcher() {
    }

    public static boolean hasListeners(@Nullable ILuaEngine engine,
                                       @NotNull String serviceName,
                                       @NotNull String signalName) {
        return resolveWithListeners(engine, serviceName, signalName) != null;
    }

    public static void fire(@Nullable ILuaEngine engine,
                            @NotNull String serviceName,
                            @NotNull String signalName) {
        CachedSignal cached = resolveWithListeners(engine, serviceName, signalName);
        if (cached != null) {
            engine.callFunction(cached.fireSync(), cached.signal());
        }
    }

    public static void fireNumber(@Nullable ILuaEngine engine,
                                  @NotNull String serviceName,
                                  @NotNull String signalName,
                                  double value) {
        CachedSignal cached = resolveWithListeners(engine, serviceName, signalName);
        if (cached != null) {
            engine.callFunction(cached.fireSync(), cached.signal(), engine.wrapNumber(value));
        }
    }

    public static void fireWithArgs(@Nullable ILuaEngine engine,
                                    @NotNull String serviceName,
                                    @NotNull String signalName,
                                    @NotNull Supplier<ILuaValue[]> argsSupplier) {
        CachedSignal cached = resolveWithListeners(engine, serviceName, signalName);
        if (cached == null) {
            return;
        }
        ILuaValue[] payload = argsSupplier.get();
        ILuaValue[] callArgs = new ILuaValue[payload.length + 1];
        callArgs[0] = cached.signal();
        System.arraycopy(payload, 0, callArgs, 1, payload.length);
        engine.callFunction(cached.fireSync(), callArgs);
    }

    private static @Nullable CachedSignal resolveWithListeners(@Nullable ILuaEngine engine,
                                                               @NotNull String serviceName,
                                                               @NotNull String signalName) {
        if (engine == null) {
            return null;
        }
        CachedSignal cached = resolveSignal(engine, serviceName, signalName);
        if (cached == null || !hasActiveListeners(cached.signal())) {
            return null;
        }
        return cached;
    }

    private static @Nullable CachedSignal resolveSignal(@NotNull ILuaEngine engine,
                                                        @NotNull String serviceName,
                                                        @NotNull String signalName) {
        Map<String, Map<String, CachedSignal>> byServiceMap =
                SIGNAL_CACHE.computeIfAbsent(engine, k -> new ConcurrentHashMap<>());
        Map<String, CachedSignal> bySignalMap = byServiceMap.get(serviceName);
        CachedSignal cached = bySignalMap != null ? bySignalMap.get(signalName) : null;
        if (cached != null) {
            return cached;
        }
        cached = lookupSignal(engine, serviceName, signalName);
        if (cached != null) {
            byServiceMap.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>())
                    .put(signalName, cached);
        }
        return cached;
    }

    private static @Nullable CachedSignal lookupSignal(@NotNull ILuaEngine engine,
                                                       @NotNull String serviceName,
                                                       @NotNull String signalName) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue serviceVal = globals.rawget(serviceName);
        if (serviceVal == null || !serviceVal.isTable()) {
            return null;
        }
        ILuaValue signalVal = serviceVal.asTable().rawget(signalName);
        if (signalVal == null || !signalVal.isTable()) {
            return null;
        }
        ILuaTable signal = signalVal.asTable();
        ILuaValue fireSync = resolveFireSync(globals, signal);
        if (fireSync == null) {
            return null;
        }
        return new CachedSignal(signal, fireSync);
    }

    private static boolean hasActiveListeners(@NotNull ILuaTable signal) {
        ILuaValue listeners = signal.rawget(LISTENERS_FIELD);
        return listeners != null && listeners.isTable() && listeners.asTable().length() > 0;
    }

    private static @Nullable ILuaValue resolveFireSync(@NotNull ILuaTable globals, @NotNull ILuaTable signal) {
        ILuaValue fireFn = signal.rawget(SIGNAL_FIRE_SYNC);
        if (fireFn != null && fireFn.isFunction()) {
            return fireFn;
        }
        ILuaValue signalClass = globals.rawget(SIGNAL_CLASS_NAME);
        if (signalClass == null || !signalClass.isTable()) {
            return null;
        }
        ILuaTable classTable = signalClass.asTable();
        ILuaValue classFire = classTable.rawget(SIGNAL_FIRE_SYNC);
        if (classFire != null && classFire.isFunction()) {
            return classFire;
        }
        ILuaValue asyncFire = classTable.rawget(SIGNAL_FIRE);
        return asyncFire != null && asyncFire.isFunction() ? asyncFire : null;
    }
}
