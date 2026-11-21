package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class CommandAliasUnregisterTest {

    private static final String MOD = "test_mod";
    private static final String NAME = "hello";
    private static final String ALIAS = "hi";
    private static final String ONE_ALIAS_DEF = "{ Aliases = { 'hi' }, Handler = function() end }";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine registerOneAlias() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, NAME, ONE_ALIAS_DEF);
        return engine;
    }

    @Test
    void unregisterViaAliasRemovesPrimary() {
        ILuaEngine engine = registerOneAlias();
        CommandTestFixture.unregister(engine, ALIAS);

        assertNull(CommandServiceImpl.get(NAME),
            "unregistering through an alias must remove the primary path");
    }

    @Test
    void unregisterViaAliasRemovesAlias() {
        ILuaEngine engine = registerOneAlias();
        CommandTestFixture.unregister(engine, ALIAS);

        assertNull(CommandServiceImpl.get(ALIAS),
            "unregistering through an alias must remove the alias path itself");
    }

    @Test
    void unregisterByPrimaryRemovesAliases() {
        ILuaEngine engine = registerOneAlias();
        CommandTestFixture.unregister(engine, NAME);

        assertNull(CommandServiceImpl.get(ALIAS),
            "unregistering by the primary path must remove every alias of that command");
    }
}
