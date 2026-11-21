package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuaCommandWrapperReloadTest {

    private static final String PING = "ping";
    private static final String OLD_DEF =
        "{ Handler = function(sender, args) _G._engine = 'old' end }";
    private static final String NEW_DEF =
        "{ Handler = function(sender, args) _G._engine = 'new' end }";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private static ILuaEngine engineRegistering(String definition) {
        ILuaEngine engine = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_ID);
        CommandTestFixture.register(engine, PING, definition);
        return engine;
    }

    @Test
    void firstEngineHandlesBeforeReload() {
        ILuaEngine engine = engineRegistering(OLD_DEF);
        new LuaCommandWrapper(PING).execute(new MockCommandSender(true, 4));

        assertEquals("old", CommandWrapperFixture.globalString(engine, "_engine"),
            "before reload the wrapper must dispatch to the first engine's handler");
    }

    @Test
    void reloadDispatchesToNewestHandler() {
        engineRegistering(OLD_DEF);
        LuaCommandWrapper wrapper = new LuaCommandWrapper(PING);
        wrapper.execute(new MockCommandSender(true, 4));

        ILuaEngine engine = engineRegistering(NEW_DEF);
        wrapper.execute(new MockCommandSender(true, 4));

        assertEquals("new", CommandWrapperFixture.globalString(engine, "_engine"),
            "after reload the Brigadier node must dispatch to the newest engine's handler");
    }

    @Test
    void reloadDoesNotAccumulateDuplicates() {
        engineRegistering(OLD_DEF);
        engineRegistering(NEW_DEF);

        assertEquals(1, CommandServiceImpl.getSnapshot().size(),
            "a reload that re-registers the same mod and name must not add a second entry");
    }
}
