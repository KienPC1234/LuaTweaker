package com.luatweaker.spawn;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpawnRuleServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private SpawnRuleServiceImpl service;

    @BeforeEach
    void setup() {
        engine = new CobaltLuaEngine();
        service = new SpawnRuleServiceImpl(engine);
    }

    @Test
    void registerHandler_StoresFunction() {
        engine.executeString("handler = function(dimensionId, players) return {} end", "test_handler");
        service.registerHandler("minecraft:test", engine.getGlobalEnvironment().rawget("handler"));
        assertNotNull(service.getHandler("minecraft:test"));
    }

    @Test
    void registerHandler_RejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler("bad id!", engine.wrapNumber(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler("minecraft:test", engine.wrapNumber(5)));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler("minecraft:test", "not a function"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler("minecraft:test", null));
    }

    @Test
    void registerHandler_OverwritesPreviousHandler() {
        engine.executeString("handlerA = function(dimensionId, players) return {} end", "test_ha");
        engine.executeString("handlerB = function(dimensionId, players) return {} end", "test_hb");
        ILuaValue handlerB = engine.getGlobalEnvironment().rawget("handlerB");
        service.registerHandler("minecraft:test", engine.getGlobalEnvironment().rawget("handlerA"));
        service.registerHandler("minecraft:test", handlerB);
        assertSame(handlerB, service.getHandler("minecraft:test"));
    }

    @Test
    void clearHandler_RemovesOnlyThatDimension() {
        engine.executeString("handler = function(dimensionId, players) return {} end", "test_clear_handler");
        service.registerHandler("minecraft:test", engine.getGlobalEnvironment().rawget("handler"));
        service.registerHandler("minecraft:other", engine.getGlobalEnvironment().rawget("handler"));
        service.clearHandler("minecraft:test");
        assertNull(service.getHandler("minecraft:test"));
        assertNotNull(service.getHandler("minecraft:other"));
    }

    @Test
    void clearHandler_RejectsInvalidDimensionId() {
        assertThrows(IllegalArgumentException.class, () -> service.clearHandler("Bad Id!"));
    }

    @Test
    void clearAll_RemovesEverything() {
        engine.executeString("handler = function(dimensionId, players) return {} end", "test_clear_all");
        service.registerHandler("minecraft:test", engine.getGlobalEnvironment().rawget("handler"));
        service.registerHandler("minecraft:other", engine.getGlobalEnvironment().rawget("handler"));
        service.clearAll();
        assertNull(service.getHandler("minecraft:test"));
        assertNull(service.getHandler("minecraft:other"));
    }

    @Test
    void getHandler_ReturnsNullWhenUnset() {
        assertNull(service.getHandler("minecraft:test"));
    }

    @Test
    void getEngine_ReturnsOwnedEngine() {
        assertSame(engine, service.getEngine());
    }
}
