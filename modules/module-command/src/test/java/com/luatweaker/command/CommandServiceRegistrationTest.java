package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandServiceRegistrationTest {

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

    private Registration register(String definition) {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        assertTrue(CommandTestFixture.register(engine, NAME, definition),
            "definition must be accepted: " + definition);
        return new Registration(engine, CommandServiceImpl.get(NAME));
    }

    @Test
    void registerStoresMetadata() {
        CommandDefinition def = register(CommandTestFixture.VALID_DEF).definition();

        assertEquals(
            List.of(MOD, NAME, "A test command", 1, false, "/lt ping <x>"),
            List.of(def.modId(), def.name(), def.description(), def.permissionLevel(),
                def.consoleAllowed(), def.usage()),
            "register must store the owner, path and every supplied metadata field");
    }

    @Test
    void registerStoresOwningEngine() {
        Registration registration = register(CommandTestFixture.VALID_DEF);

        assertEquals(registration.engine(), registration.definition().engine(),
            "the definition must remember the engine that declared the handler");
    }

    @Test
    void registerStoresFunctionHandler() {
        CommandDefinition def = register(CommandTestFixture.VALID_DEF).definition();

        assertTrue(def.handler().isFunction(), "the stored handler must be the Lua function, not a copy");
    }

    @Test
    void registerAddsSingleSnapshotEntry() {
        register(CommandTestFixture.VALID_DEF);

        assertEquals(List.of(NAME), names(CommandServiceImpl.getSnapshot()),
            "a single registration must appear exactly once in the snapshot");
    }

    @Test
    void registerAppliesDefaults() {
        CommandDefinition def = register(CommandTestFixture.MINIMAL_DEF).definition();

        assertEquals(List.of(2, true, NAME, ""),
            List.of(def.permissionLevel(), def.consoleAllowed(), def.description(), def.usage()),
            "omitted fields need op level 2, console allowed, name as description and empty usage");
    }

    private static List<String> names(List<CommandDefinition> definitions) {
        return definitions.stream().map(CommandDefinition::name).toList();
    }

    private record Registration(ILuaEngine engine, CommandDefinition definition) {
    }
}
