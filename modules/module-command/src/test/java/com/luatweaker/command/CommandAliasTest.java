package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandAliasTest {

    private static final String MOD = "test_mod";
    private static final String NAME = "hello";
    private static final String ALIAS = "hi";
    private static final String SECOND_ALIAS = "greet";

    private static final String TWO_ALIASES_DEF =
        "{ Aliases = { 'hi', 'greet' }, Handler = function() end }";
    private static final String DEDUPE_DEF = "{ Aliases = { 'hi', 'hi', 'hello' }, Handler = function() end }";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine registerTwoAliases() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, NAME, TWO_ALIASES_DEF);
        return engine;
    }

    @Test
    void registerParsesAliases() {
        registerTwoAliases();

        assertEquals(List.of(ALIAS, SECOND_ALIAS), CommandServiceImpl.get(NAME).aliases(),
            "the Aliases table must be parsed in declaration order");
    }

    @Test
    void registerResolvesAliasPaths() {
        registerTwoAliases();

        List<String> resolved = new ArrayList<>();
        if (CommandServiceImpl.get(ALIAS) != null) {
            resolved.add(ALIAS);
        }
        if (CommandServiceImpl.get(SECOND_ALIAS) != null) {
            resolved.add(SECOND_ALIAS);
        }

        assertEquals(List.of(ALIAS, SECOND_ALIAS), resolved,
            "every alias path must resolve to the command definition");
    }

    @Test
    void aliasesDoNotDuplicateSnapshot() {
        registerTwoAliases();

        assertEquals(1, CommandServiceImpl.getSnapshot().size(),
            "aliases must not be listed as separate snapshot entries");
    }

    @Test
    void registerDeduplicatesAliasesAndSelfReferences() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, NAME, DEDUPE_DEF);

        assertEquals(List.of(ALIAS), CommandServiceImpl.get(NAME).aliases(),
            "duplicate aliases and self references must be dropped");
    }

    @Test
    void registerRejectsInvalidAliases() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        List<Boolean> results = new ArrayList<>();
        results.add(CommandTestFixture.register(engine, NAME, "{ Aliases = { 'Bad Alias' }, Handler = function() end }"));
        results.add(CommandTestFixture.register(engine, NAME, "{ Aliases = { 'reload' }, Handler = function() end }"));
        results.add(CommandTestFixture.register(engine, NAME, "{ Aliases = 'hi', Handler = function() end }"));
        results.add(CommandTestFixture.register(engine, NAME, "{ Aliases = { 42 }, Handler = function() end }"));

        assertEquals(List.of(false, false, false, false), results,
            "invalid, reserved, non-table and non-string aliases must all be rejected");
    }

}
