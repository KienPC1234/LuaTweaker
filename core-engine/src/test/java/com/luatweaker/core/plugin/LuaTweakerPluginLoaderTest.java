package com.luatweaker.core.plugin;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.plugin.ILuaTweakerPlugin;
import com.luatweaker.api.plugin.LuaTweakerPluginRegistry;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.core.vm.NativeModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LuaTweakerPluginLoaderTest {
    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @AfterEach
    void clearRegistry() {
        LuaTweakerPluginRegistry.clearForTests();
        NativeModuleResolver.clearDynamicAliasesForTests();
    }

    @LuaDoc(description = "Test plugin API.")
    public interface TestApi {
        @LuaDoc(description = "Returns a marker.", returnType = "string")
        String marker();
    }

    public static final class GoodPlugin implements ILuaTweakerPlugin {
        final List<String> order;
        final int rank;

        GoodPlugin(List<String> order, int rank) {
            this.order = order;
            this.rank = rank;
        }

        @Override public @NotNull String pluginId() {
            return "test-plugin-" + rank;
        }

        @Override public @NotNull String pluginVersion() {
            return "1.0.0";
        }

        @Override public int loadOrder() {
            return rank;
        }

        @Override public void registerServices(@NotNull ILuaEngine engine) {
            order.add(pluginId());
            TestApi impl = () -> "ok-" + rank;
            LuaBinder.bind(engine, "TestPlugin" + rank, impl, TestApi.class);
        }

        @Override public @NotNull Map<String, String[]> requireAliases() {
            return Map.of("LuaTweaker.TestPlugin" + rank, new String[]{"TestPlugin" + rank});
        }

        @Override public @NotNull Map<String, Class<?>> stubServices() {
            return Map.of("TestPlugin" + rank, TestApi.class);
        }
    }

    public static final class BadPlugin implements ILuaTweakerPlugin {
        @Override public @NotNull String pluginId() {
            return "bad-plugin";
        }

        @Override public @NotNull String pluginVersion() {
            return "0.0.1";
        }

        @Override public void registerServices(@NotNull ILuaEngine engine) {
            throw new IllegalStateException("boom from plugin");
        }
    }

    @Test
    void pluginsRegisterInLoadOrderAndAliasesResolve() {
        List<String> order = new ArrayList<>();
        LuaTweakerPluginRegistry.register(new GoodPlugin(order, 20));
        LuaTweakerPluginRegistry.register(new GoodPlugin(order, 5));

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaTweakerPluginLoader.loadPlugins(engine);

        assertEquals(List.of("test-plugin-5", "test-plugin-20"), order);
        engine.executeString(
            "assert(TestPlugin5:marker() == 'ok-5')\n"
                + "local viaRequire = require('LuaTweaker.TestPlugin20')\n"
                + "assert(viaRequire:marker() == 'ok-20')\n",
            "plugin_alias_test");
        assertNull(engine.getAndClearLastExecutionError());
        NativeModuleResolver.clearDynamicAliasesForTests();
    }

    @Test
    void failingPluginDoesNotAbortOthers() {
        List<String> order = new ArrayList<>();
        LuaTweakerPluginRegistry.register(new BadPlugin());
        LuaTweakerPluginRegistry.register(new GoodPlugin(order, 7));

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaTweakerPluginLoader.loadPlugins(engine);

        assertEquals(List.of("test-plugin-7"), order);
        engine.executeString("assert(TestPlugin7:marker() == 'ok-7')\n", "plugin_survive_test");
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void blankPluginIdIsRefused() {
        LuaTweakerPluginRegistry.register(new ILuaTweakerPlugin() {
            @Override public @NotNull String pluginId() {
                return "  ";
            }

            @Override public @NotNull String pluginVersion() {
                return "1";
            }

            @Override public void registerServices(@NotNull ILuaEngine engine) {
                throw new AssertionError("must never be called");
            }
        });
        assertTrue(LuaTweakerPluginRegistry.list().isEmpty());
        assertTrue(LuaTweakerPluginLoader.discoverPlugins().isEmpty());
    }
}
