package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Registration and clearing semantics of the dimension handler table, with a real Lua VM.
 */
class SpawnRuleServiceImplTest {

    private static final String TEST_DIMENSION = "minecraft:test";
    private static final String OTHER_DIMENSION = "minecraft:other";
    private static final String HANDLER_GLOBAL = "handler";
    private static final String HANDLER_SCRIPT =
            "handler = function(dimensionId, players) return {} end";
    private static final String SECOND_HANDLER_SCRIPT =
            "handlerB = function(dimensionId, players) return {} end";

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
    void storesFunctionRegisteredFromLua() {
        engine.executeString(HANDLER_SCRIPT, "test_handler");

        service.registerHandler(TEST_DIMENSION, handler(HANDLER_GLOBAL));

        Assertions.assertNotNull(service.getHandler(TEST_DIMENSION),
                "a Lua function must be stored for the dimension it was registered under");
    }

    @Test
    void rejectsMalformedRegistrations() {
        Assertions.assertAll("malformed handler registrations must all be rejected",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler("bad id!", engine.wrapNumber(1)),
                "a dimension id with spaces/exclamation must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler(TEST_DIMENSION, engine.wrapNumber(5)),
                "a Lua number must be rejected as a handler"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler(TEST_DIMENSION, "not a function"),
                "a Java string must be rejected as a handler"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.registerHandler(TEST_DIMENSION, null),
                "a null handler must be rejected"));
    }

    @Test
    void overwritesPreviousHandlerForTheSameDimension() {
        engine.executeString(HANDLER_SCRIPT, "test_ha");
        engine.executeString(SECOND_HANDLER_SCRIPT, "test_hb");
        service.registerHandler(TEST_DIMENSION, handler(HANDLER_GLOBAL));
        ILuaValue replacement = handler("handlerB");

        service.registerHandler(TEST_DIMENSION, replacement);

        Assertions.assertSame(replacement, service.getHandler(TEST_DIMENSION),
                "re-registering a dimension must replace the previous handler");
    }

    @Test
    void clearHandlerRemovesOnlyThatDimension() {
        registerBothDimensions();

        service.clearHandler(TEST_DIMENSION);

        Assertions.assertAll("clearing one dimension must not touch the other",
            () -> Assertions.assertNull(service.getHandler(TEST_DIMENSION),
                "clearing a dimension must remove its handler"),
            () -> Assertions.assertNotNull(service.getHandler(OTHER_DIMENSION),
                "clearing one dimension must keep another dimension's handler"));
    }

    @Test
    void clearHandlerRejectsInvalidDimensionId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.clearHandler("Bad Id!"),
                "clearing an invalid dimension id must be rejected");
    }

    @Test
    void clearAllRemovesEveryRegisteredDimension() {
        registerBothDimensions();

        service.clearAll();

        for (String dimension : List.of(TEST_DIMENSION, OTHER_DIMENSION)) {
            Assertions.assertNull(service.getHandler(dimension),
                    "clearAll must remove the handler for " + dimension);
        }
    }

    private void registerBothDimensions() {
        engine.executeString(HANDLER_SCRIPT, "test_clear_handler");
        service.registerHandler(TEST_DIMENSION, handler(HANDLER_GLOBAL));
        service.registerHandler(OTHER_DIMENSION, handler(HANDLER_GLOBAL));
    }

    private ILuaValue handler(String globalName) {
        return engine.getGlobalEnvironment().rawget(globalName);
    }
}
