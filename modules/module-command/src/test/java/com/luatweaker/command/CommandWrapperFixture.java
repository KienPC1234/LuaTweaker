package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;

import java.util.List;

/**
 * Registers one command on a fresh engine and drives the Brigadier-facing
 * wrapper, then exposes the Lua globals the handlers wrote.
 */
final class CommandWrapperFixture {

    private CommandWrapperFixture() {
    }

    static CommandRun execute(String name, String definition, MockCommandSender sender, String... args) {
        ILuaEngine engine = bind(name, definition);
        int result = new LuaCommandWrapper(name).execute(sender, args);
        return new CommandRun(engine, sender, result);
    }

    static CommandRun executeRaw(String name, String definition, MockCommandSender sender, String raw) {
        ILuaEngine engine = bind(name, definition);
        int result = new LuaCommandWrapper(name).executeRaw(sender, raw);
        return new CommandRun(engine, sender, result);
    }

    static SuggestRun suggest(String name, String definition, String typedTail) {
        ILuaEngine engine = bind(name, definition);
        List<String> suggestions = new LuaCommandWrapper(name).suggest(new MockCommandSender(true, 4), typedTail);
        return new SuggestRun(engine, suggestions);
    }

    static boolean handlerRan(ILuaEngine engine) {
        ILuaValue ran = engine.getGlobalEnvironment().rawget("_ran");
        return ran != null && !ran.isNil();
    }

    static String globalString(ILuaEngine engine, String key) {
        return engine.getGlobalEnvironment().rawget(key).asString();
    }

    static boolean globalBoolean(ILuaEngine engine, String key) {
        return engine.getGlobalEnvironment().rawget(key).asBoolean();
    }

    static int globalInt(ILuaEngine engine, String key) {
        return engine.getGlobalEnvironment().rawget(key).asInt();
    }

    private static ILuaEngine bind(String name, String definition) {
        ILuaEngine engine = CommandTestFixture.engineWithMod(CommandTestFixture.MOD_ID);
        if (!CommandTestFixture.register(engine, name, definition)) {
            throw new IllegalStateException("test definition for /" + name + " failed to register");
        }
        return engine;
    }
}
