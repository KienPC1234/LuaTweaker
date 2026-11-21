package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Minimal in-memory {@link ILuaEngine} for unit tests: hashmap-backed tables, no VM. Lifecycle and
 * registration no-ops come from {@link FakeEngineLifecycle}; this class owns value conversion and
 * the global table.
 */
final class FakeEngine implements FakeEngineLifecycle {

    private final FakeTable globalsTable = new FakeTable();

    @Override
    public ILuaTable globals() {
        return globalsTable;
    }

    @Override
    public ILuaTable createTable() {
        return new FakeTable();
    }

    @Override
    public ILuaValue callFunction(ILuaValue function, ILuaValue... args) {
        return FakeValue.NIL;
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
        return FakeValues.toLuaValue(obj);
    }

    @Override
    public ILuaTable getGlobalEnvironment() {
        return globalsTable;
    }
}
