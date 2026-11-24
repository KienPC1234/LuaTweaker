package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CommandConflictTest {

    private static final String HELLO = "hello";
    private static final String GREET = "greet";
    private static final String SPAWN = "summon";

    private static final String GREET_ALIAS_DEF = "{ Aliases = { 'greet' }, Handler = function() end }";
    private static final String V1_DEF =
        "{ Description = 'v1', Handler = function() _G._handlerVersion = 'v1' end }";
    private static final String V2_DEF =
        "{ Description = 'v2', Handler = function() _G._handlerVersion = 'v2' end }";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void foreignAliasRegistrationIsRejected() {
        ILuaEngine modA = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_A);
        CommandTestFixture.register(modA, HELLO, GREET_ALIAS_DEF);
        ILuaEngine modB = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_B);

        assertFalse(CommandTestFixture.register(modB, GREET, CommandTestFixture.MINIMAL_DEF),
            "another mod must not claim a path already owned through an alias");
    }

    @Test
    void foreignAliasKeepsOriginalOwner() {
        ILuaEngine modA = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_A);
        CommandTestFixture.register(modA, HELLO, GREET_ALIAS_DEF);
        ILuaEngine modB = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_B);
        CommandTestFixture.register(modB, GREET, CommandTestFixture.MINIMAL_DEF);

        assertEquals(CommandTestFixture.MOD_A, CommandServiceImpl.get(GREET).modId(),
            "a rejected alias claim must leave the original owner in place");
    }

    @Test
    void crossModReRegistrationIsRejected() {
        ILuaEngine modA = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_A);
        CommandTestFixture.register(modA, SPAWN, CommandTestFixture.VALID_DEF);
        ILuaEngine modB = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_B);

        assertFalse(CommandTestFixture.register(modB, SPAWN, CommandTestFixture.MINIMAL_DEF),
            "a different mod must not take over an existing command name");
    }

    @Test
    void crossModReRegistrationKeepsOriginalOwner() {
        ILuaEngine modA = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_A);
        CommandTestFixture.register(modA, SPAWN, CommandTestFixture.VALID_DEF);
        ILuaEngine modB = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_B);
        CommandTestFixture.register(modB, SPAWN, CommandTestFixture.MINIMAL_DEF);

        assertEquals(CommandTestFixture.MOD_A, CommandServiceImpl.get(SPAWN).modId(),
            "the original owner must keep a command a foreign mod tried to claim");
    }

    @Test
    void sameModReRegisterKeepsSingleSnapshotEntry() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_ID);
        CommandTestFixture.register(engine, HELLO, V1_DEF);
        CommandTestFixture.register(engine, HELLO, V2_DEF);

        assertEquals(1, CommandServiceImpl.getSnapshot().size(),
            "re-registration by the same mod must replace instead of accumulate");
    }

    @Test
    void sameModReRegisterUsesLatestDefinition() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_ID);
        CommandTestFixture.register(engine, HELLO, V1_DEF);
        CommandTestFixture.register(engine, HELLO, V2_DEF);

        assertEquals("v2", CommandServiceImpl.get(HELLO).description(),
            "the newest definition must win after re-registration");
    }
}
