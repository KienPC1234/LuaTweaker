package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandUnregisterTest {

    private static final String MOD = "test_mod";
    private static final String NAME = "ping";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine registerPing() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, NAME, CommandTestFixture.VALID_DEF);
        return engine;
    }

    @Test
    void unregisterByOwnerReturnsTrue() {
        ILuaEngine engine = registerPing();

        assertTrue(CommandTestFixture.unregister(engine, NAME),
            "the owner must be able to unregister its own command");
    }

    @Test
    void unregisterByOwnerRemovesCommand() {
        ILuaEngine engine = registerPing();
        CommandTestFixture.unregister(engine, NAME);

        assertNull(CommandServiceImpl.get(NAME), "an owner unregister must drop the command");
    }

    @Test
    void unregisterByForeignModIsRejected() {
        registerPing();
        ILuaEngine modB = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_B);

        assertFalse(CommandTestFixture.unregister(modB, NAME),
            "a foreign mod must not unregister another mod's command");
    }

    @Test
    void unregisterByForeignModKeepsCommand() {
        registerPing();
        ILuaEngine modB = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_B);
        CommandTestFixture.unregister(modB, NAME);

        assertEquals(MOD, CommandServiceImpl.get(NAME).modId(),
            "a rejected foreign unregister must leave the owner untouched");
    }

    @Test
    void unregisterUnknownReturnsFalse() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);

        assertFalse(CommandTestFixture.unregister(engine, "nope"),
            "unregistering an unknown command must report false");
    }

    @Test
    void clearEmptiesSnapshot() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, "a", CommandTestFixture.VALID_DEF);
        CommandTestFixture.register(engine, "b", CommandTestFixture.VALID_DEF);

        CommandServiceImpl.clear();

        assertEquals(0, CommandServiceImpl.getSnapshot().size(), "clear must empty the snapshot");
    }

    @Test
    void clearRemovesCommands() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, "a", CommandTestFixture.VALID_DEF);
        CommandTestFixture.register(engine, "b", CommandTestFixture.VALID_DEF);

        CommandServiceImpl.clear();

        assertEquals(List.of(false, false),
            List.of(CommandServiceImpl.get("a") != null, CommandServiceImpl.get("b") != null),
            "clear must remove both registered commands");
    }
}
