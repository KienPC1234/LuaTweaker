package com.luatweaker.api.vm;

import com.luatweaker.api.annotation.LuaDoc;
import java.io.File;

@LuaDoc(description = "Core Lua engine interface for executing scripts and managing bindings.")
public interface ILuaEngine {
    @LuaDoc(
        description = "Sets the root directory for resolving file-based Lua modules via require().",
        params = {"luaDirectory: File"}
    )
    void setLuaDirectory(File luaDirectory);

    void registerService(String name, ILuaTable service);
    void registerService(String name, Object service);
    void registerGlobal(String name, ILuaValue value);
    void registerGlobal(String name, ILuaFunction function);
    void executeScript(File file, String context);
    void executeString(String code, String name);
    
    ILuaTable createTable();
    ILuaValue wrapUserdata(Object userdata);
    ILuaValue wrapString(String value);
    ILuaValue wrapNumber(double value);
    ILuaValue wrapBoolean(boolean value);
    ILuaValue nilValue();

    ILuaValue toLuaValue(Object obj);
    ILuaValue callFunction(ILuaValue function, ILuaValue... args);

    /**
     * Invokes a Lua function and returns ALL return values (not just the first).
     * Empty when the function is nil or errored.
     */
    default ILuaValue[] callFunctionMulti(ILuaValue function, ILuaValue... args) {
        ILuaValue result = callFunction(function, args);
        return (result == null || result.isNil()) ? new ILuaValue[0] : new ILuaValue[]{result};
    }

    @LuaDoc(
        description = "Returns and clears the last Lua execution error captured by this engine, if any.",
        returnType = "string|nil"
    )
    default String getAndClearLastExecutionError() { return null; }

    @LuaDoc(
        description = "Wraps an arbitrary Java object as a Lua value using the engine's dynamic bridge.",
        params = {"obj: any"},
        returnType = "ILuaValue"
    )
    default ILuaValue wrapDynamicJavaObject(Object obj) { return wrapUserdata(obj); }

    @LuaDoc(description = "Stops background Lua tasks and releases VM resources; safe to call more than once.")
    default void shutdown() { }

    /**
     * Runs a Java-side operation that touches Lua state under the engine's execution lock.
     * Render-thread code that mutates Lua tables must use this instead of touching them directly.
     * The default runs inline for engine-neutral test doubles.
     */
    default void runExclusive(Runnable action) { action.run(); }

    ILuaTable getGlobalEnvironment();
}
