package com.luatweaker.core.stub;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Static registry bridging Lua bindings to the stub generator.
 *
 * <p>Each {@code *LuaBinding} that binds a service via {@code LuaBinder.bind}
 * registers its {@code (name, apiInterface)} pair here so
 * {@code LtvmStubGenerator} can emit autocomplete stubs even for services
 * that {@code LuaTweakerMod.generateStubs()} never registered directly.
 * Idempotent: re-registering the same name is a no-op for snapshot purposes.
 */
public final class StubCatalog {
    private static final Map<String, Class<?>> CATALOG = new ConcurrentHashMap<>();

    private StubCatalog() {}

    public static void register(@NotNull String name, @NotNull Class<?> api) {
        if (name != null && api != null) {
            CATALOG.putIfAbsent(name, api);
        }
    }

    @NotNull
    public static Map<String, Class<?>> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CATALOG));
    }

    /** Test-only hook to isolate unit tests sharing this static registry. */
    static void clearForTests() {
        CATALOG.clear();
    }
}
