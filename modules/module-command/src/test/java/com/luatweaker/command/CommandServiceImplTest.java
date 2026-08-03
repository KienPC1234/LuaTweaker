package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registration policy of the shared Lua command registry: ownership attribution,
 * reserved names, invalid definitions and cross-mod conflict rejection.
 */
public class CommandServiceImplTest {

    @BeforeEach
    public void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    public static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine newEngineWithMod(String modId) {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, modId);
        return engine;
    }

    private static void setModGlobal(ILuaEngine engine, String modId) {
        ILuaTable modTable = engine.createTable();
        modTable.rawset("ID", engine.wrapString(modId));
        engine.getGlobalEnvironment().rawset("mod", modTable);
        engine.getGlobalEnvironment().rawset("Mod", modTable);
    }

    private static void register(ILuaEngine engine, String name, String definitionSnippet) {
        engine.executeString(
                "local ok = Commands:Register('" + name + "', " + definitionSnippet + ")\n" +
                "if ok then _G._registerResult = 'true' else _G._registerResult = 'false' end",
                "register_test"
        );
        assertEquals("true", engine.getGlobalEnvironment().rawget("_registerResult").asString(),
                "Register('" + name + "') must return true");
    }

    private static void registerExpectFalse(ILuaEngine engine, String name, String definitionSnippet) {
        engine.executeString(
                "local ok = Commands:Register('" + name + "', " + definitionSnippet + ")\n" +
                "if ok then _G._registerResult = 'true' else _G._registerResult = 'false' end",
                "register_reject_test"
        );
        assertEquals("false", engine.getGlobalEnvironment().rawget("_registerResult").asString(),
                "Register('" + name + "') must be rejected");
    }

    private static final String VALID_DEF =
            "{ Description = 'A test command', PermissionLevel = 1, ConsoleAllowed = false, Usage = '/lt ping <x>', Handler = function(sender, args) end }";

    @Test
    public void registerValidCommandStoresDefinition() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "ping", VALID_DEF);

        CommandDefinition def = CommandServiceImpl.get("ping");
        assertNotNull(def, "command must be registered");
        assertEquals("test_mod", def.modId());
        assertEquals("ping", def.name());
        assertEquals("A test command", def.description());
        assertEquals(1, def.permissionLevel());
        assertFalse(def.consoleAllowed());
        assertEquals("/lt ping <x>", def.usage());
        assertEquals(engine, def.engine());
        assertTrue(def.handler().isFunction());

        assertEquals(1, CommandServiceImpl.getSnapshot().size());
        assertEquals("ping", CommandServiceImpl.getSnapshot().get(0).name());
    }

    @Test
    public void registerAppliesDefaults() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "ping", "{ Handler = function() end }");

        CommandDefinition def = CommandServiceImpl.get("ping");
        assertNotNull(def);
        assertEquals(2, def.permissionLevel(), "permission level must default to 2");
        assertTrue(def.consoleAllowed(), "console must be allowed by default");
        assertEquals("ping", def.description(), "description must default to the command name");
        assertEquals("", def.usage(), "usage must default to empty");
    }

    @Test
    public void registerRejectsMissingHandler() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        registerExpectFalse(engine, "ping", "{ Description = 'no handler' }");
        assertNull(CommandServiceImpl.get("ping"));
    }

    @Test
    public void registerRejectsReservedCoreNames() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        for (String reserved : new String[] { "reload", "hand", "help", "doctor" }) {
            registerExpectFalse(engine, reserved, "{ Handler = function() end }");
            assertNull(CommandServiceImpl.get(reserved), reserved + " must stay reserved");
        }
    }

    @Test
    public void registerRejectsInvalidNames() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        String definition = "{ Handler = function() end }";
        registerExpectFalse(engine, "HasUpper", definition);
        registerExpectFalse(engine, "has space", definition);
        registerExpectFalse(engine, "", definition);
        registerExpectFalse(engine, "has.dot", definition);
        engine.executeString(
                "local ok = Commands:Register('" + "a".repeat(33) + "', { Handler = function() end })\n" +
                "if ok then _G._registerResult = 'true' else _G._registerResult = 'false' end",
                "register_long_name"
        );
        assertEquals("false", engine.getGlobalEnvironment().rawget("_registerResult").asString(),
                "names longer than 32 chars must be rejected");
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void registerSupportsNestedPaths() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "shop/buy", "{ Handler = function() end }");
        register(engine, "shop/list", "{ Handler = function() end }");

        assertNotNull(CommandServiceImpl.get("shop/buy"), "nested path must be registered");
        assertNotNull(CommandServiceImpl.get("shop/list"), "commands sharing a root must coexist");
        assertEquals(2, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void registerRejectsMalformedPaths() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        String definition = "{ Handler = function() end }";
        registerExpectFalse(engine, "/shop", definition);
        registerExpectFalse(engine, "shop/", definition);
        registerExpectFalse(engine, "shop//buy", definition);
        registerExpectFalse(engine, "a/b/c/d/e", definition);
        registerExpectFalse(engine, "Shop/buy", definition);
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void registerRejectsReservedRootNames() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        registerExpectFalse(engine, "lt", "{ Handler = function() end }");
        registerExpectFalse(engine, "luatweaker", "{ Handler = function() end }");
        registerExpectFalse(engine, "lt/anything", "{ Handler = function() end }");
        registerExpectFalse(engine, "luatweaker/reload", "{ Handler = function() end }");
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void registerAllowsReservedNameAsNestedSegment() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "shop/reload", "{ Handler = function() end }");
        assertNotNull(CommandServiceImpl.get("shop/reload"),
                "reserved names must only be blocked at the top level or as a full path");
    }

    @Test
    public void registerParsesAliases() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "hello", "{ Aliases = { 'hi', 'greet' }, Handler = function() end }");

        CommandDefinition def = CommandServiceImpl.get("hello");
        assertNotNull(def);
        assertEquals(List.of("hi", "greet"), def.aliases());
        // Aliases resolve like primary names.
        assertNotNull(CommandServiceImpl.get("hi"));
        assertNotNull(CommandServiceImpl.get("greet"));
        assertEquals(1, CommandServiceImpl.getSnapshot().size(), "aliases must not appear in the snapshot twice");
    }

    @Test
    public void registerDeduplicatesAliasesAndSelfReferences() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "hello", "{ Aliases = { 'hi', 'hi', 'hello' }, Handler = function() end }");
        CommandDefinition def = CommandServiceImpl.get("hello");
        assertEquals(List.of("hi"), def.aliases(), "duplicate aliases and self references must be dropped");
    }

    @Test
    public void registerRejectsInvalidAliases() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        registerExpectFalse(engine, "hello", "{ Aliases = { 'Bad Alias' }, Handler = function() end }");
        registerExpectFalse(engine, "hello", "{ Aliases = { 'reload' }, Handler = function() end }");
        registerExpectFalse(engine, "hello", "{ Aliases = 'hi', Handler = function() end }");
        registerExpectFalse(engine, "hello", "{ Aliases = { 42 }, Handler = function() end }");
        assertNull(CommandServiceImpl.get("hello"));
    }

    @Test
    public void foreignAliasConflictIsRejected() {
        ILuaEngine modA = newEngineWithMod("mod_a");
        register(modA, "hello", "{ Aliases = { 'greet' }, Handler = function() end }");

        ILuaEngine modB = newEngineWithMod("mod_b");
        registerExpectFalse(modB, "greet", "{ Handler = function() end }");
        assertNotNull(CommandServiceImpl.get("greet"), "the original alias owner must keep the path");
        assertEquals("mod_a", CommandServiceImpl.get("greet").modId());
    }

    @Test
    public void registerParsesSuggestionProviders() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "static", "{ Suggestions = { 'a', 'b' }, Handler = function() end }");
        register(engine, "dynamic", "{ Suggestions = function(sender, args) return { 'x' } end, Handler = function() end }");

        CommandDefinition staticDef = CommandServiceImpl.get("static");
        assertNotNull(staticDef.suggestionProvider());
        assertTrue(staticDef.suggestionProvider().isTable());

        CommandDefinition dynamicDef = CommandServiceImpl.get("dynamic");
        assertNotNull(dynamicDef.suggestionProvider());
        assertTrue(dynamicDef.suggestionProvider().isFunction());
    }

    @Test
    public void registerRejectsInvalidSuggestions() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        registerExpectFalse(engine, "a", "{ Suggestions = { 42 }, Handler = function() end }");
        registerExpectFalse(engine, "b", "{ Suggestions = 'nope', Handler = function() end }");
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void unregisterRemovesAliasesToo() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "hello", "{ Aliases = { 'hi' }, Handler = function() end }");
        engine.executeString("local ok = Commands:Unregister('hello')", "unregister_alias");
        assertNull(CommandServiceImpl.get("hello"));
        assertNull(CommandServiceImpl.get("hi"), "unregistering must remove aliases as well");
    }

    @Test
    public void registerRejectsOutOfRangePermissionLevel() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        registerExpectFalse(engine, "a", "{ PermissionLevel = 5, Handler = function() end }");
        registerExpectFalse(engine, "b", "{ PermissionLevel = -1, Handler = function() end }");
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void registerRejectsWrongFieldTypes() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        registerExpectFalse(engine, "a", "{ PermissionLevel = '2', Handler = function() end }");
        registerExpectFalse(engine, "b", "{ ConsoleAllowed = 'yes', Handler = function() end }");
        registerExpectFalse(engine, "c", "{ Description = 42, Handler = function() end }");
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
    }

    @Test
    public void sameModReRegisterReplacesDefinition() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "ping", "{ Description = 'v1', Handler = function() _G._handlerVersion = 'v1' end }");
        register(engine, "ping", "{ Description = 'v2', Handler = function() _G._handlerVersion = 'v2' end }");

        assertEquals(1, CommandServiceImpl.getSnapshot().size(), "re-registration must replace, not accumulate");
        CommandDefinition def = CommandServiceImpl.get("ping");
        assertEquals("v2", def.description());
        assertEquals("test_mod", def.modId());
    }

    @Test
    public void differentModClaimingSameNameIsRejected() {
        ILuaEngine modA = newEngineWithMod("mod_a");
        register(modA, "spawn", VALID_DEF);

        ILuaEngine modB = newEngineWithMod("mod_b");
        registerExpectFalse(modB, "spawn", "{ Handler = function() end }");

        CommandDefinition def = CommandServiceImpl.get("spawn");
        assertNotNull(def);
        assertEquals("mod_a", def.modId(), "the original owner must keep the command");
    }

    @Test
    public void registerWithoutModContextIsRejected() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        registerExpectFalse(engine, "ping", "{ Handler = function() end }");
        assertNull(CommandServiceImpl.get("ping"));
    }

    @Test
    public void unregisterByOwnerRemovesCommand() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "ping", VALID_DEF);
        engine.executeString("local ok = Commands:Unregister('ping')", "unregister_test");
        assertNull(CommandServiceImpl.get("ping"), "owner must be able to unregister its own command");
    }

    @Test
    public void unregisterByOtherModIsRejected() {
        ILuaEngine modA = newEngineWithMod("mod_a");
        register(modA, "ping", VALID_DEF);

        ILuaEngine modB = newEngineWithMod("mod_b");
        modB.executeString("local ok = Commands:Unregister('ping')", "unregister_foreign");
        assertNotNull(CommandServiceImpl.get("ping"), "a foreign mod must not unregister another mod's command");
        assertEquals("mod_a", CommandServiceImpl.get("ping").modId());
    }

    @Test
    public void unregisterUnknownCommandIsRejected() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        engine.executeString("local ok = Commands:Unregister('nope')", "unregister_unknown");
        assertNull(CommandServiceImpl.get("nope"));
    }

    @Test
    public void clearRemovesAllCommands() {
        ILuaEngine engine = newEngineWithMod("test_mod");
        register(engine, "a", VALID_DEF);
        register(engine, "b", VALID_DEF);
        assertEquals(2, CommandServiceImpl.getSnapshot().size());

        CommandServiceImpl.clear();
        assertEquals(0, CommandServiceImpl.getSnapshot().size());
        assertNull(CommandServiceImpl.get("a"));
        assertNull(CommandServiceImpl.get("b"));
    }
}
