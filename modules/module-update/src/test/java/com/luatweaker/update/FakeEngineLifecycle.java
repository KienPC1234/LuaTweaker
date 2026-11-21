package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.io.File;

/**
 * No-op {@link ILuaEngine} surface for the fake: without a VM there is no working directory, no
 * service registry and nothing to execute. Only global registration is real, because the update
 * and web services read the {@code mod} global from it.
 */
interface FakeEngineLifecycle extends ILuaEngine {

    /** The fake's global environment, shared with {@link ILuaEngine#getGlobalEnvironment()}. */
    ILuaTable globals();

    @Override
    default void setLuaDirectory(File luaDirectory) {
        // No VM, so there is no working directory to point luava at.
    }

    @Override
    default void registerService(String name, ILuaTable service) {
        // Update/web tests never resolve services through the engine.
    }

    @Override
    default void registerService(String name, Object service) {
        // Update/web tests never resolve services through the engine.
    }

    @Override
    default void registerGlobal(String name, ILuaValue value) {
        globals().rawset(name, value);
    }

    @Override
    default void registerGlobal(String name, ILuaFunction function) {
        globals().rawset(name, function);
    }

    @Override
    default void executeScript(File file, String context) {
        // Mods loaded by these tests only contain comments; there is no VM to run them.
    }

    @Override
    default void executeString(String code, String name) {
        // See executeScript: the fake engine never evaluates Lua.
    }
}
