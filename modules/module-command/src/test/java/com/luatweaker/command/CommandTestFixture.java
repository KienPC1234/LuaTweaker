package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;

/**
 * Creates a Lua engine with the Commands service bound and an optional mod
 * identity installed, plus the definition snippets shared by the command tests.
 */
final class CommandTestFixture {

    static final String MOD_ID = "test_mod";
    static final String MOD_A = "mod_a";
    static final String MOD_B = "mod_b";

    static final String MINIMAL_DEF = "{ Handler = function() end }";

    static final String VALID_DEF = "{ Description = 'A test command', PermissionLevel = 1, "
        + "ConsoleAllowed = false, Usage = '/lt ping <x>', Handler = function(sender, args) end }";

    static final String PERMISSION_ONE_DEF = "{ PermissionLevel = 1, Handler = function() end }";

    static final String HELLO_DEF = """
        { Handler = function(sender, args)
            _G._senderName = sender.Name
            _G._senderIsPlayer = sender.IsPlayer
            _G._senderHeldItem = sender.HeldItemId
            _G._senderHasPerm = sender:HasPermission(2)
            _G._argCount = #args
            _G._arg1 = args[1]
            sender:SendSuccess('Hi ' .. args[1])
            return true
          end }
        """;

    static final String FAIL_DEF = "{ Handler = function(sender, args) return false end }";
    static final String SILENT_DEF = "{ Handler = function(sender, args) end }";
    static final String BOOM_DEF = "{ Handler = function(sender, args) error('kaboom') end }";

    private CommandTestFixture() {
    }

    static ILuaEngine engineWithMod(String modId) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        CommandLuaBinding.registerBindings(engine);
        setModGlobal(engine, modId);
        return engine;
    }

    static boolean register(ILuaEngine engine, String name, String definition) {
        engine.executeString(
            "local ok = Commands:Register('" + name + "', " + definition + ")\n"
                + "_G._registerResult = ok and 'true' or 'false'",
            "register_test"
        );
        return "true".equals(engine.getGlobalEnvironment().rawget("_registerResult").asString());
    }

    static boolean unregister(ILuaEngine engine, String name) {
        engine.executeString(
            "local ok = Commands:Unregister('" + name + "')\n"
                + "_G._unregisterResult = ok and 'true' or 'false'",
            "unregister_test"
        );
        return "true".equals(engine.getGlobalEnvironment().rawget("_unregisterResult").asString());
    }

    private static void setModGlobal(ILuaEngine engine, String modId) {
        ILuaTable modTable = engine.createTable();
        modTable.rawset("ID", engine.wrapString(modId));
        engine.getGlobalEnvironment().rawset("mod", modTable);
        engine.getGlobalEnvironment().rawset("Mod", modTable);
    }
}
