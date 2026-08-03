package com.luatweaker.command;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end dispatch of a Lua-registered command through LuaCommandWrapper:
 * sender table building, permission/console gating, result mapping and live
 * definition resolution across engine reloads.
 */
public class LuaCommandWrapperTest {

    private static class MockSender implements ICommandSender {
        final List<String> messages = new ArrayList<>();
        final List<Integer> permissionChecks = new ArrayList<>();
        final boolean player;
        final int grantedLevel;

        MockSender(boolean player, int grantedLevel) {
            this.player = player;
            this.grantedLevel = grantedLevel;
        }

        @Override
        public void sendMessage(String message) { messages.add("MSG:" + message); }
        @Override
        public void sendSuccess(String message) { messages.add("OK:" + message); }
        @Override
        public void sendError(String message) { messages.add("ERR:" + message); }
        @Override
        public boolean hasPermission(int level) {
            permissionChecks.add(level);
            return grantedLevel >= level;
        }
        @Override
        public boolean isPlayer() { return player; }
        @Override
        public IPlayer getPlayer() { return null; }
        @Override
        public String getName() { return "TestPlayer"; }
        @Override
        public String getHeldItemId() { return "minecraft:diamond_sword"; }
    }

    @BeforeEach
    public void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    public static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private static void setModGlobal(ILuaEngine engine, String modId) {
        ILuaTable modTable = engine.createTable();
        modTable.rawset("ID", engine.wrapString(modId));
        engine.getGlobalEnvironment().rawset("mod", modTable);
        engine.getGlobalEnvironment().rawset("Mod", modTable);
    }

    @Test
    public void handlerReceivesSenderAndArgs() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('hello', {\n" +
                "  Handler = function(sender, args)\n" +
                "    _G._senderName = sender.Name\n" +
                "    _G._senderIsPlayer = sender.IsPlayer\n" +
                "    _G._senderHeldItem = sender.HeldItemId\n" +
                "    _G._senderHasPerm = sender:HasPermission(2)\n" +
                "    _G._argCount = #args\n" +
                "    _G._arg1 = args[1]\n" +
                "    sender:SendSuccess('Hi ' .. args[1])\n" +
                "    return true\n" +
                "  end\n" +
                "})",
                "register_hello"
        );

        MockSender sender = new MockSender(true, 4);
        int result = new LuaCommandWrapper("hello").execute(sender, new String[] { "world" });

        assertEquals(1, result, "truthy handler return must map to Brigadier success");
        assertTrue(sender.permissionChecks.contains(2), "wrapper must re-check permission at execution time");
        assertEquals("Hi world", sender.messages.get(0).substring(3), "SendSuccess must reach the sender");

        ILuaTable globals = engine.getGlobalEnvironment();
        assertEquals("TestPlayer", globals.rawget("_senderName").asString());
        assertEquals(true, globals.rawget("_senderIsPlayer").asBoolean());
        assertEquals("minecraft:diamond_sword", globals.rawget("_senderHeldItem").asString());
        assertEquals(true, globals.rawget("_senderHasPerm").asBoolean());
        assertEquals(1, globals.rawget("_argCount").asInt());
        assertEquals("world", globals.rawget("_arg1").asString());
    }

    @Test
    public void handlerFalseMapsToFailure() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('fail', { Handler = function(sender, args) return false end })",
                "register_fail"
        );
        assertEquals(0, new LuaCommandWrapper("fail").execute(new MockSender(true, 4), new String[0]));
    }

    @Test
    public void handlerNilMapsToSuccess() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('silent', { Handler = function(sender, args) end })",
                "register_silent"
        );
        assertEquals(1, new LuaCommandWrapper("silent").execute(new MockSender(true, 4), new String[0]));
    }

    @Test
    public void handlerLuaErrorMapsToFailureAndIsLogged() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('boom', { Handler = function(sender, args) error('kaboom') end })",
                "register_boom"
        );
        MockSender sender = new MockSender(true, 4);
        // The engine logs the Lua error loudly; the wrapper maps it to failure.
        assertEquals(0, new LuaCommandWrapper("boom").execute(sender, new String[0]),
                "a Lua error inside the handler must map to command failure");
        assertEquals(0, new LuaCommandWrapper("boom").execute(sender, new String[0]),
                "repeated execution must stay deterministic");
    }

    @Test
    public void permissionDeniedBlocksExecution() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('op', { PermissionLevel = 2, Handler = function(sender, args) _G._ran = true end })",
                "register_op"
        );
        MockSender sender = new MockSender(true, 0);
        assertEquals(0, new LuaCommandWrapper("op").execute(sender, new String[0]));
        assertTrue(sender.messages.stream().anyMatch(m -> m.startsWith("ERR:") && m.contains("operator level 2")),
                "denied sender must receive the permission error");
        ILuaValue ran = engine.getGlobalEnvironment().rawget("_ran");
        assertTrue(ran == null || ran.isNil(), "handler must not run without permission");
    }

    @Test
    public void consoleDisallowedBlocksConsoleSender() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('player_only', { ConsoleAllowed = false, Handler = function(sender, args) _G._ran = true end })",
                "register_player_only"
        );
        MockSender console = new MockSender(false, 4);
        assertEquals(0, new LuaCommandWrapper("player_only").execute(console, new String[0]));
        assertTrue(console.messages.stream().anyMatch(m -> m.startsWith("ERR:") && m.contains("players")),
                "console sender must be rejected with a player-only error");
        ILuaValue ran = engine.getGlobalEnvironment().rawget("_ran");
        assertTrue(ran == null || ran.isNil(), "handler must not run for the console");
    }

    @Test
    public void consoleDisallowedAllowsPlayerSender() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('player_only', { ConsoleAllowed = false, Handler = function(sender, args) _G._ran = true end })",
                "register_player_only"
        );
        assertEquals(1, new LuaCommandWrapper("player_only").execute(new MockSender(true, 4), new String[0]));
        assertEquals(true, engine.getGlobalEnvironment().rawget("_ran").asBoolean());
    }

    @Test
    public void unregisteredCommandFailsLoudly() {
        MockSender sender = new MockSender(true, 4);
        assertEquals(0, new LuaCommandWrapper("ghost").execute(sender, new String[0]));
        assertTrue(sender.messages.stream().anyMatch(m -> m.startsWith("ERR:") && m.contains("not registered")),
                "unregistered commands must fail loudly");
    }

    @Test
    public void executeRawPreservesExactTypedText() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('echo', { Handler = function(sender, args, raw) _G._raw = raw; _G._tokens = #args end })",
                "register_echo"
        );

        // Extra spacing must be preserved in the raw tail and lost in the token array.
        assertEquals(1, new LuaCommandWrapper("echo").executeRaw(new MockSender(true, 4), "  hello   world  "));
        assertEquals("  hello   world  ", engine.getGlobalEnvironment().rawget("_raw").asString(),
                "the raw tail must be exactly as typed");
        assertEquals(2, engine.getGlobalEnvironment().rawget("_tokens").asInt(),
                "the token array must still be split on whitespace");
    }

    @Test
    public void staticSuggestionsAreReturned() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('hello', { Suggestions = { 'world', 'minecraft' }, Handler = function() end })",
                "register_suggest_static"
        );

        List<String> suggestions = new LuaCommandWrapper("hello").suggest(new MockSender(true, 4), "hel");
        assertEquals(List.of("world", "minecraft"), suggestions);
    }

    @Test
    public void functionSuggestionsReceiveSenderAndArgs() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('shop/buy', {\n" +
                "  Suggestions = function(sender, args)\n" +
                "    _G._suggestSender = sender.Name\n" +
                "    _G._suggestArgs = #args\n" +
                "    return { 'sword', 'pickaxe' }\n" +
                "  end,\n" +
                "  Handler = function() end\n" +
                "})",
                "register_suggest_fn"
        );

        List<String> suggestions = new LuaCommandWrapper("shop/buy").suggest(new MockSender(true, 4), "buy ru");
        assertEquals(List.of("sword", "pickaxe"), suggestions);
        assertEquals("TestPlayer", engine.getGlobalEnvironment().rawget("_suggestSender").asString());
        assertEquals(2, engine.getGlobalEnvironment().rawget("_suggestArgs").asInt(),
                "the suggestion function must receive the typed tokens");
    }

    @Test
    public void commandWithoutSuggestionsReturnsEmptyList() {
        ILuaEngine engine = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, "test_mod");
        engine.executeString(
                "Commands:Register('plain', { Handler = function() end })",
                "register_plain"
        );
        assertTrue(new LuaCommandWrapper("plain").suggest(new MockSender(true, 4), "x").isEmpty());
    }

    @Test
    public void reloadReplacesHandlerLive() {
        // First engine registers the command (startup phase).
        ILuaEngine engine1 = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine1);
        setModGlobal(engine1, "test_mod");
        engine1.executeString(
                "Commands:Register('ping', { Handler = function(sender, args) _G._engine = 'old' end })",
                "register_v1"
        );

        LuaCommandWrapper wrapper = new LuaCommandWrapper("ping");
        wrapper.execute(new MockSender(true, 4), new String[0]);
        assertEquals("old", engine1.getGlobalEnvironment().rawget("_engine").asString());

        // /lt reload: new engine re-registers the same mod+name -> replace semantics.
        ILuaEngine engine2 = new CobaltLuaEngine();
        CommandLuaBinding.registerBindings(engine2);
        setModGlobal(engine2, "test_mod");
        engine2.executeString(
                "Commands:Register('ping', { Handler = function(sender, args) _G._engine = 'new' end })",
                "register_v2"
        );

        wrapper.execute(new MockSender(true, 4), new String[0]);
        assertEquals(1, CommandServiceImpl.getSnapshot().size(), "reload must not accumulate duplicates");
        assertEquals("new", engine2.getGlobalEnvironment().rawget("_engine").asString(),
                "the Brigadier node must dispatch to the newest engine's handler after reload");
    }
}
