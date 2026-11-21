package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Shared fixture for the World bulk-edit / structure / effect binding tests: a fresh Lua engine
 * bound to the interaction service plus a recording PAL double.
 */
class WorldEditFixture {

    protected static final String OVERWORLD = "minecraft:overworld";
    protected static final String NETHER = "minecraft:the_nether";

    protected WorldEditRecorder recorder;
    protected ILuaEngine engine;

    @BeforeEach
    void setUp() {
        recorder = new WorldEditRecorder();
        Platform.setInteraction(recorder);
        engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
        Platform.setInteraction(null);
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    /** Executes Lua and fails the test if the engine recorded a script error. */
    protected static void executeLua(ILuaEngine engine, String code, String name) {
        engine.executeString(code, name);
        String error = engine.getAndClearLastExecutionError();
        assertNull(error, "Lua script failed: " + error);
    }

    protected ILuaValue worldFunction(String name) {
        return engine.getGlobalEnvironment().rawget("World").asTable().rawget(name);
    }
}
