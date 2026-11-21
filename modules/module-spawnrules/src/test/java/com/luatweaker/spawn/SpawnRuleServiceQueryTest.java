package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Read-only queries on the spawn rule service (handler lookup and engine ownership).
 */
class SpawnRuleServiceQueryTest {

    private static final String TEST_DIMENSION = "minecraft:test";

    private ILuaEngine engine;
    private SpawnRuleServiceImpl service;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        service = new SpawnRuleServiceImpl(engine);
    }

    @Test
    void handlerIsNullWhenUnset() {
        Assertions.assertNull(service.getHandler(TEST_DIMENSION),
                "an unregistered dimension must report no handler");
    }

    @Test
    void engineIsTheInstancePassedAtConstruction() {
        Assertions.assertSame(engine, service.getEngine(),
                "the service must expose the engine it was constructed with");
    }
}
