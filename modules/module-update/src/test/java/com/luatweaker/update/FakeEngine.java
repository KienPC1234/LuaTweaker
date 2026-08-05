package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Minimal in-memory ILuaEngine for unit tests: hashmap-backed tables, no VM.
 */
public class FakeEngine implements ILuaEngine {

    private final FakeTable globals = new FakeTable();

    @Override
    public void setLuaDirectory(File luaDirectory) {
    }

    @Override
    public void registerService(String name, ILuaTable service) {
    }

    @Override
    public void registerService(String name, Object service) {
    }

    @Override
    public void registerGlobal(String name, ILuaValue value) {
        globals.rawset(name, value);
    }

    @Override
    public void registerGlobal(String name, ILuaFunction function) {
        globals.rawset(name, function);
    }

    @Override
    public void executeScript(File file, String context) {
    }

    @Override
    public void executeString(String code, String name) {
    }

    @Override
    public ILuaTable createTable() {
        return new FakeTable();
    }

    @Override
    public ILuaValue wrapUserdata(Object userdata) {
        return new FakeValue(userdata);
    }

    @Override
    public ILuaValue wrapString(String value) {
        return new FakeValue(value);
    }

    @Override
    public ILuaValue wrapNumber(double value) {
        return new FakeValue(value);
    }

    @Override
    public ILuaValue wrapBoolean(boolean value) {
        return new FakeValue(value);
    }

    @Override
    public ILuaValue nilValue() {
        return FakeValue.NIL;
    }

    @Override
    public ILuaValue toLuaValue(Object obj) {
        if (obj == null) return FakeValue.NIL;
        if (obj instanceof ILuaValue v) return v;
        if (obj instanceof String s) return new FakeValue(s);
        if (obj instanceof Number n) return new FakeValue(n.doubleValue());
        if (obj instanceof Boolean b) return new FakeValue(b);
        if (obj instanceof Map<?, ?> map) {
            FakeTable table = new FakeTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                table.rawset(String.valueOf(entry.getKey()), toLuaValue(entry.getValue()));
            }
            return table;
        }
        if (obj instanceof List<?> list) {
            FakeTable table = new FakeTable();
            int i = 1;
            for (Object item : list) {
                table.rawset(i++, toLuaValue(item));
            }
            return table;
        }
        return new FakeValue(obj);
    }

    @Override
    public ILuaValue callFunction(ILuaValue function, ILuaValue... args) {
        return FakeValue.NIL;
    }

    @Override
    public ILuaTable getGlobalEnvironment() {
        return globals;
    }
}
