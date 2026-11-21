package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandFieldValidationTest {

    private static final String MOD = "test_mod";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private boolean registerWithFreshEngine(String name, String definition) {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        return CommandTestFixture.register(engine, name, definition);
    }

    @Test
    void registerRejectsOutOfRangePermissionLevel() {
        assertEquals(List.of(false, false),
            List.of(
                registerWithFreshEngine("a", "{ PermissionLevel = 5, Handler = function() end }"),
                registerWithFreshEngine("b", "{ PermissionLevel = -1, Handler = function() end }")),
            "permission levels outside 0..4 must be rejected");
    }

    @Test
    void registerRejectsFractionalPermissionLevel() {
        assertFalse(registerWithFreshEngine("a", "{ PermissionLevel = 2.5, Handler = function() end }"),
            "a fractional permission level must not be silently truncated");
    }

    @Test
    void registerRejectsWrongFieldTypes() {
        assertEquals(List.of(false, false, false),
            List.of(
                registerWithFreshEngine("a", "{ PermissionLevel = '2', Handler = function() end }"),
                registerWithFreshEngine("b", "{ ConsoleAllowed = 'yes', Handler = function() end }"),
                registerWithFreshEngine("c", "{ Description = 42, Handler = function() end }")),
            "wrongly typed PermissionLevel, ConsoleAllowed and Description must be rejected");
    }

    @Test
    void registerRejectsInvalidSuggestions() {
        assertEquals(List.of(false, false),
            List.of(
                registerWithFreshEngine("a", "{ Suggestions = { 42 }, Handler = function() end }"),
                registerWithFreshEngine("b", "{ Suggestions = 'nope', Handler = function() end }")),
            "a suggestion list of numbers and a bare string must both be rejected");
    }

    @Test
    void registerStoresStaticSuggestionTable() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, "static", "{ Suggestions = { 'a', 'b' }, Handler = function() end }");

        assertTrue(CommandServiceImpl.get("static").suggestionProvider().isTable(),
            "a static Suggestions table must be stored as a table provider");
    }

    @Test
    void registerStoresFunctionSuggestionProvider() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, "dynamic",
            "{ Suggestions = function(sender, args) return { 'x' } end, Handler = function() end }");

        assertTrue(CommandServiceImpl.get("dynamic").suggestionProvider().isFunction(),
            "a Suggestions function must be stored as a function provider");
    }

    @Test
    void registerWithoutModContextIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        CommandLuaBinding.registerBindings(engine);

        assertFalse(CommandTestFixture.register(engine, "ping", CommandTestFixture.MINIMAL_DEF),
            "registration without a mod identity must be rejected");
    }
}
