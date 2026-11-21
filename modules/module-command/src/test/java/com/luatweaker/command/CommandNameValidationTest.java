package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommandNameValidationTest {

    private static final String MOD = "test_mod";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private List<Boolean> registrationResults(String... names) {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        List<Boolean> results = new ArrayList<>();
        for (String name : names) {
            results.add(CommandTestFixture.register(engine, name, CommandTestFixture.MINIMAL_DEF));
        }
        return results;
    }

    @Test
    void registerRejectsMissingHandler() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);

        assertFalse(CommandTestFixture.register(engine, "ping", "{ Description = 'no handler' }"),
            "a definition without a Handler function must be rejected");
    }

    @Test
    void registerRejectsReservedCoreNames() {
        assertEquals(List.of(false, false, false, false),
            registrationResults("reload", "hand", "help", "doctor"),
            "core /lt subcommand names must stay reserved");
    }

    @Test
    void registerRejectsInvalidNames() {
        assertEquals(List.of(false, false, false, false, false),
            registrationResults("HasUpper", "has space", "", "has.dot", "a".repeat(33)),
            "uppercase, space, blank, dot and over-long names must all be rejected");
    }

    @Test
    void registerRejectsMalformedPaths() {
        assertEquals(List.of(false, false, false, false, false),
            registrationResults("/shop", "shop/", "shop//buy", "a/b/c/d/e", "Shop/buy"),
            "leading, trailing and doubled slashes plus over-deep paths must be rejected");
    }

    @Test
    void registerRejectsReservedRootNames() {
        assertEquals(List.of(false, false, false, false),
            registrationResults("lt", "luatweaker", "lt/anything", "luatweaker/reload"),
            "the /lt and /luatweaker roots must stay reserved");
    }

    @Test
    void registerSupportsNestedPaths() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);
        CommandTestFixture.register(engine, "shop/buy", CommandTestFixture.MINIMAL_DEF);
        CommandTestFixture.register(engine, "shop/list", CommandTestFixture.MINIMAL_DEF);

        assertEquals(List.of(true, true),
            List.of(CommandServiceImpl.get("shop/buy") != null, CommandServiceImpl.get("shop/list") != null),
            "commands sharing a root segment must coexist as separate paths");
    }

    @Test
    void registerAllowsReservedNameAsNestedSegment() {
        ILuaEngine engine = CommandTestFixture.engineWithMod(MOD);

        CommandTestFixture.register(engine, "shop/reload", CommandTestFixture.MINIMAL_DEF);

        assertNotNull(CommandServiceImpl.get("shop/reload"),
            "reserved words must only be blocked as the top-level segment");
    }
}
